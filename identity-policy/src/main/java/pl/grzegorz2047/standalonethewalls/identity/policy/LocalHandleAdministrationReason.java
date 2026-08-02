package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.text.Normalizer;
import java.util.Objects;

/** Bounded NFC audit reason without control characters. */
public record LocalHandleAdministrationReason(String value) {
    public static final int MAXIMUM_CODE_POINTS = 256;

    public LocalHandleAdministrationReason {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("administration reason must be non-blank and trimmed");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("administration reason must use NFC normalization");
        }
        if (value.codePointCount(0, value.length()) > MAXIMUM_CODE_POINTS) {
            throw new IllegalArgumentException("administration reason exceeds the maximum length");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("administration reason cannot contain controls");
        }
    }
}
