package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical lowercase SHA-256 digest. */
public record Sha256Digest(String value) {
    private static final Pattern LOWERCASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public Sha256Digest {
        Objects.requireNonNull(value, "value");
        if (!LOWERCASE_SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be 64 lowercase hexadecimal characters");
        }
    }
}
