package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;

/** Auditable local trust record. It contains only public server identity data. */
public record ServerTrustRecord(
        ServerReference reference, ServerId serverId, Source source, String reason) {
    public ServerTrustRecord {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(source, "source");
        reason = Objects.requireNonNull(reason, "reason").strip();
        int length = reason.codePointCount(0, reason.length());
        if (length < 1
                || length > 256
                || reason.codePoints().anyMatch(ServerTrustRecord::isForbiddenCodePoint)) {
            throw new IllegalArgumentException(
                    "trust reason must contain 1 to 256 safe printable code points");
        }
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || codePoint == 0x202A
                || codePoint == 0x202B
                || codePoint == 0x202C
                || codePoint == 0x202D
                || codePoint == 0x202E
                || codePoint == 0x2066
                || codePoint == 0x2067
                || codePoint == 0x2068
                || codePoint == 0x2069;
    }

    public enum Source {
        TOFU,
        EXPECTED_PIN,
        EXPLICIT_REPLACEMENT
    }
}
