package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/** Unicode presentation name normalized to NFC and bounded independently of identity. */
public record LocalDisplayName(String value) {
    public static final int MAXIMUM_CODE_POINTS = 64;
    public static final int MAXIMUM_UTF8_BYTES = 192;
    public static final int MAXIMUM_INPUT_UTF16_CODE_UNITS = 512;

    public LocalDisplayName {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAXIMUM_INPUT_UTF16_CODE_UNITS) {
            throw new IllegalArgumentException("display name input exceeds the validation limit");
        }
        requireWellFormedUtf16(value);
        requireAllowedCodePoints(value);

        String normalized = trimUnicodeWhitespace(Normalizer.normalize(value, Normalizer.Form.NFC));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("display name cannot be empty");
        }
        requireWellFormedUtf16(normalized);
        requireAllowedCodePoints(normalized);
        if (!Normalizer.isNormalized(normalized, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("display name normalization failed");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAXIMUM_CODE_POINTS) {
            throw new IllegalArgumentException("display name exceeds the code point limit");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_UTF8_BYTES) {
            throw new IllegalArgumentException("display name exceeds the UTF-8 byte limit");
        }
        value = normalized;
    }

    private static String trimUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static void requireWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("display name contains malformed Unicode");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("display name contains malformed Unicode");
            }
        }
    }

    private static void requireAllowedCodePoints(String value) {
        value.codePoints().forEach(LocalDisplayName::requireAllowedCodePoint);
    }

    private static void requireAllowedCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        if (codePoint == 0
                || type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR) {
            throw new IllegalArgumentException("display name contains a prohibited code point");
        }
    }
}
