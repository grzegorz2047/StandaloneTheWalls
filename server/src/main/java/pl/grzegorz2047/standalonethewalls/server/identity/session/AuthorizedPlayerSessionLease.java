package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transfers one authorized session while retaining its global admission-capacity slot.
 *
 * <p>The receiver owns the session and must call {@link #closeAsync()} when that ownership ends.
 * Capacity is released only after the underlying session close stage completes.
 */
public final class AuthorizedPlayerSessionLease {
    private final AuthorizedPlayerSessionQueue queue;
    private final AuthorizedPlayerSession session;
    private final AtomicBoolean active = new AtomicBoolean(true);

    AuthorizedPlayerSessionLease(
            AuthorizedPlayerSessionQueue queue, AuthorizedPlayerSession session) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.session = Objects.requireNonNull(session, "session");
    }

    public AuthorizedPlayerSession session() {
        return session;
    }

    public boolean isActive() {
        return active.get();
    }

    public CompletionStage<Void> closeAsync() {
        if (!active.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletionStage<Void> closeStage;
        try {
            closeStage = Objects.requireNonNull(session.closeAsync(), "session close stage");
        } catch (RuntimeException exception) {
            queue.release(this);
            throw exception;
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        closeStage.whenComplete(
                (ignored, failure) -> {
                    queue.release(this);
                    if (failure == null) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
        return result;
    }

    @Override
    public String toString() {
        return "AuthorizedPlayerSessionLease[session=" + session + ", active=" + active.get() + ']';
    }
}
