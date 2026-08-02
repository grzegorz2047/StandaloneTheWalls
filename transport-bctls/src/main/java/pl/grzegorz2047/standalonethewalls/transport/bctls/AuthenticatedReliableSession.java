package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Verified player identity and post-authentication reliable application channel. */
public final class AuthenticatedReliableSession {
    private final UUID sessionId;
    private final Tls13SessionSecurity security;
    private final PlayerId playerId;
    private final CanonicalHandle handle;
    private final ReliableChannel reliableChannel;

    AuthenticatedReliableSession(
            BootstrappedReliableSession session, PlayerId playerId, CanonicalHandle handle) {
        Objects.requireNonNull(session, "session");
        this.sessionId = session.sessionId();
        this.security = session.security();
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.reliableChannel = new PostIdentityReliableChannel(session.reliableChannel());
    }

    public UUID sessionId() {
        return sessionId;
    }

    public Tls13SessionSecurity security() {
        return security;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public CanonicalHandle handle() {
        return handle;
    }

    public ReliableChannel reliableChannel() {
        return reliableChannel;
    }

    public boolean isOpen() {
        return reliableChannel.isOpen();
    }

    public CompletionStage<Void> closeAsync() {
        return reliableChannel.close();
    }

    @Override
    public String toString() {
        return "AuthenticatedReliableSession[sessionId="
                + sessionId
                + ", serverId="
                + security.serverId()
                + ", playerId="
                + playerId
                + ", handle="
                + handle
                + ", open="
                + isOpen()
                + ']';
    }
}
