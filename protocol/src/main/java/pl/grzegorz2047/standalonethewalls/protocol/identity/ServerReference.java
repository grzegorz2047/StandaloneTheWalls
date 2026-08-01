package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable adapter-selected server reference, such as a canonical host and port. */
public record ServerReference(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9._:-]{2,254}");

    public ServerReference {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid server reference");
        }
    }
}
