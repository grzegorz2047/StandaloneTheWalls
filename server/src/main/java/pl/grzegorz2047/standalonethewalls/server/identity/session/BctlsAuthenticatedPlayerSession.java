package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;

/** Transport adapter that preserves the already-authenticated Bouncy Castle TLS session. */
final class BctlsAuthenticatedPlayerSession implements AuthenticatedPlayerSession {
    private final AuthenticatedReliableSession session;

    BctlsAuthenticatedPlayerSession(AuthenticatedReliableSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public UUID sessionId() {
        return session.sessionId();
    }

    @Override
    public ServerId serverId() {
        return session.security().serverId();
    }

    @Override
    public PlayerId playerId() {
        return session.playerId();
    }

    @Override
    public CanonicalHandle handle() {
        return session.handle();
    }

    @Override
    public ReliableChannel reliableChannel() {
        return session.reliableChannel();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return session.closeAsync();
    }
}
