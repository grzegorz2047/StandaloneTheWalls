package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.time.Instant;
import java.util.Objects;

/** Client-facing DTLS external-PSK material delivered only over authenticated reliable TLS. */
public record IssuedRealtimeTicket(
        RealtimeTicketIdentity identity, RealtimePreSharedKey preSharedKey, Instant expiresAt)
        implements AutoCloseable {
    public IssuedRealtimeTicket {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(preSharedKey, "preSharedKey");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public void close() {
        preSharedKey.close();
    }

    @Override
    public String toString() {
        return "IssuedRealtimeTicket[identity=opaque, preSharedKey=redacted, expiresAt="
                + expiresAt
                + ']';
    }
}
