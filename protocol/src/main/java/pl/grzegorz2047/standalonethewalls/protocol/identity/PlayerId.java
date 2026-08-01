package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable public identity derived solely from canonical public-key bytes. */
public record PlayerId(String value) {
    private static final Pattern FORMAT = Pattern.compile("sf1_[a-z2-7]{52}");

    public PlayerId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid Sunderfront playerId");
        }
    }

    public static PlayerId fromPublicKey(byte[] subjectPublicKeyInfo) throws IdentityException {
        Objects.requireNonNull(subjectPublicKeyInfo, "subjectPublicKeyInfo");
        byte[] canonical = IdentityKeys.decodePublicKey(subjectPublicKeyInfo).getEncoded();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return new PlayerId("sf1_" + Base32Lowercase.encode(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_ENCODING_UNAVAILABLE,
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
