package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;

/** Authenticated TLS security metadata and reliable channel after UUID agreement. */
public final class BootstrappedReliableSession {
    private final UUID sessionId;
    private final Tls13SessionSecurity security;
    private final ReliableChannel reliableChannel;

    BootstrappedReliableSession(
            UUID sessionId, Tls13SessionSecurity security, ReliableChannel reliableChannel) {
        if (!TlsSessionBootstrapCodec.isValidSessionId(sessionId)) {
            throw new IllegalArgumentException("sessionId must be a non-zero RFC 4122 UUIDv4");
        }
        this.sessionId = sessionId;
        this.security = Objects.requireNonNull(security, "security");
        this.reliableChannel = Objects.requireNonNull(reliableChannel, "reliableChannel");
    }

    public UUID sessionId() {
        return sessionId;
    }

    public Tls13SessionSecurity security() {
        return security;
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
        return "BootstrappedReliableSession[sessionId="
                + sessionId
                + ", serverId="
                + security.serverId()
                + ", open="
                + isOpen()
                + ']';
    }
}
