package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/** Authorization handle; Unicode display names are intentionally separate. */
public record CanonicalHandle(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_]{3,24}");

    public CanonicalHandle {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("handle must match [a-z0-9_]{3,24}");
        }
    }
}
