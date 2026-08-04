package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Opaque 128-bit external-PSK identity. The value is not an authenticator by itself. */
public final class RealtimeTicketIdentity {
    public static final int LENGTH_BYTES = 16;

    private final byte[] bytes;

    public RealtimeTicketIdentity(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH_BYTES) {
            throw new IllegalArgumentException("realtime ticket identity must contain 16 bytes");
        }
        this.bytes = bytes.clone();
    }

    public byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RealtimeTicketIdentity other
                && MessageDigest.isEqual(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RealtimeTicketIdentity[opaque]";
    }
}
