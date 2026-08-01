package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Arrays;
import java.util.Objects;

/** Opaque 32-byte exporter value supplied by an authenticated secure-transport adapter. */
public final class SecureChannelBinding {
    public static final int BYTES = 32;
    private final byte[] value;

    public SecureChannelBinding(byte[] value) {
        this.value = Objects.requireNonNull(value, "value").clone();
        if (value.length != BYTES) {
            throw new IllegalArgumentException(
                    "secure channel binding must contain exactly 32 bytes");
        }
    }

    public byte[] bytes() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SecureChannelBinding binding && Arrays.equals(value, binding.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "SecureChannelBinding[bytes=" + value.length + ']';
    }
}
