package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Immutable session that passed cryptographic proof and server identity admission. */
public final class AuthorizedPlayerSession {
    private static final Runnable NO_CLOSE_CALLBACK = () -> {};

    private final AuthenticatedPlayerSession session;
    private final HandleVerificationLevel verificationLevel;
    private final Runnable closeCallback;
    private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();

    AuthorizedPlayerSession(
            AuthenticatedPlayerSession session, HandleVerificationLevel verificationLevel) {
        this(session, verificationLevel, NO_CLOSE_CALLBACK);
    }

    private AuthorizedPlayerSession(
            AuthenticatedPlayerSession session,
            HandleVerificationLevel verificationLevel,
            Runnable closeCallback) {
        this.session = Objects.requireNonNull(session, "session");
        this.verificationLevel = Objects.requireNonNull(verificationLevel, "verificationLevel");
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
    }

    public UUID sessionId() {
        return session.sessionId();
    }

    public ServerId serverId() {
        return session.serverId();
    }

    public PlayerId playerId() {
        return session.playerId();
    }

    public CanonicalHandle handle() {
        return session.handle();
    }

    public HandleVerificationLevel verificationLevel() {
        return verificationLevel;
    }

    public ReliableChannel reliableChannel() {
        return session.reliableChannel();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public CompletionStage<Void> closeAsync() {
        CompletableFuture<Void> existing = closeFuture.get();
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Void> created = new CompletableFuture<>();
        if (!closeFuture.compareAndSet(null, created)) {
            return closeFuture.get();
        }
        try {
            CompletionStage<Void> stage =
                    Objects.requireNonNull(session.closeAsync(), "session close stage");
            stage.whenComplete((ignored, failure) -> completeClose(created, failure));
        } catch (RuntimeException exception) {
            completeClose(created, exception);
        }
        return created;
    }

    AuthorizedPlayerSession retainCapacityUntilClose(Runnable releaseCapacity) {
        if (closeFuture.get() != null) {
            throw new IllegalStateException("closed session cannot be transferred");
        }
        return new AuthorizedPlayerSession(session, verificationLevel, releaseCapacity);
    }

    @Override
    public String toString() {
        return "AuthorizedPlayerSession[sessionId="
                + sessionId()
                + ", serverId="
                + serverId()
                + ", verificationLevel="
                + verificationLevel
                + ", open="
                + isOpen()
                + ']';
    }

    private void completeClose(CompletableFuture<Void> target, Throwable failure) {
        Throwable completionFailure = failure;
        try {
            closeCallback.run();
        } catch (RuntimeException callbackFailure) {
            if (completionFailure == null) {
                completionFailure = callbackFailure;
            } else {
                completionFailure.addSuppressed(callbackFailure);
            }
        }
        if (completionFailure == null) {
            target.complete(null);
        } else {
            target.completeExceptionally(completionFailure);
        }
    }
}
