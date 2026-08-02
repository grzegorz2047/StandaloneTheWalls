package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Immutable session that passed cryptographic proof and server identity admission. */
public final class AuthorizedPlayerSession {
    private final AuthenticatedPlayerSession session;
    private final HandleVerificationLevel verificationLevel;

    AuthorizedPlayerSession(
            AuthenticatedPlayerSession session, HandleVerificationLevel verificationLevel) {
        this.session = Objects.requireNonNull(session, "session");
        this.verificationLevel = Objects.requireNonNull(verificationLevel, "verificationLevel");
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
        return session.closeAsync();
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
}
