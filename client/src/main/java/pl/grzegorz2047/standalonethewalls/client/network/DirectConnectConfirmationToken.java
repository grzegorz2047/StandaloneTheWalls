package pl.grzegorz2047.standalonethewalls.client.network;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Opaque single-use token whose bytes never appear in text rendering. */
public final class DirectConnectConfirmationToken {
    private final byte[] value;

    DirectConnectConfirmationToken(byte[] value) {
        Objects.requireNonNull(value, "value");
        if (value.length != 32) {
            throw new IllegalArgumentException("confirmation token must contain exactly 32 bytes");
        }
        this.value = value.clone();
    }

    boolean securelyEquals(DirectConnectConfirmationToken other) {
        return other != null && MessageDigest.isEqual(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DirectConnectConfirmationToken token && securelyEquals(token);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "DirectConnectConfirmationToken[redacted]";
    }
}
