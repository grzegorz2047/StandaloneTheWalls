package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;

/** Strict canonical Ed25519 public-key decoding. */
final class IdentityKeys {
    private IdentityKeys() {
        throw new AssertionError("No instances");
    }

    static PublicKey decodePublicKey(byte[] encoded) throws IdentityException {
        Objects.requireNonNull(encoded, "encoded");
        try {
            PublicKey key =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(encoded));
            if (!Arrays.equals(encoded, key.getEncoded())) {
                throw new IdentityException(
                        IdentityException.Code.INVALID_PUBLIC_KEY,
                        "public key encoding is not canonical");
            }
            return key;
        } catch (IdentityException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IdentityException(
                    IdentityException.Code.INVALID_PUBLIC_KEY,
                    "invalid Ed25519 public key",
                    exception);
        }
    }
}
