package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Short public server fingerprint for manual comparison; never an authoritative identifier. */
public record ServerFingerprint(String value) {
    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{4}(?:-[0-9a-f]{4}){4}");

    public ServerFingerprint {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid Sunderfront server fingerprint");
        }
    }

    public static ServerFingerprint fromPublicKey(byte[] subjectPublicKeyInfo)
            throws IdentityException {
        byte[] canonical =
                IdentityKeys.decodePublicKey(
                                Objects.requireNonNull(
                                        subjectPublicKeyInfo, "subjectPublicKeyInfo"))
                        .getEncoded();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            String hex = HexFormat.of().formatHex(digest, 0, 10);
            StringBuilder grouped = new StringBuilder(24);
            for (int offset = 0; offset < hex.length(); offset += 4) {
                if (offset > 0) {
                    grouped.append('-');
                }
                grouped.append(hex, offset, Math.min(offset + 4, hex.length()));
            }
            return new ServerFingerprint(grouped.toString());
        } catch (NoSuchAlgorithmException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_ENCODING_UNAVAILABLE,
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
