package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.time.Instant;
import java.util.Objects;

/** Server-side one-time ticket ownership transferred to one future DTLS handshake. */
public record RedeemedRealtimeTicket(
        RealtimeTicketIdentity identity,
        RealtimePreSharedKey preSharedKey,
        RealtimeTicketContext context,
        Instant expiresAt)
        implements AutoCloseable {
    public RedeemedRealtimeTicket {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(preSharedKey, "preSharedKey");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public void close() {
        preSharedKey.close();
    }

    @Override
    public String toString() {
        return "RedeemedRealtimeTicket[identity=opaque, preSharedKey=redacted, context="
                + context
                + ", expiresAt="
                + expiresAt
                + ']';
    }
}
