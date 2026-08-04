package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Immutable SHA-256 digest of the reliable TLS channel binding. */
public final class RealtimeChannelBindingDigest {
    public static final int LENGTH_BYTES = 32;

    private final byte[] bytes;

    public RealtimeChannelBindingDigest(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH_BYTES) {
            throw new IllegalArgumentException("channel binding digest must contain 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    public byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RealtimeChannelBindingDigest other
                && MessageDigest.isEqual(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RealtimeChannelBindingDigest[sha256]";
    }
}
