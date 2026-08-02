package pl.grzegorz2047.standalonethewalls.registry;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;

final class RegistryCrypto {
    private static final char[] BASE32 = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    private RegistryCrypto() {
        throw new AssertionError("No instances");
    }

    static PublicKey decodeEd25519(byte[] encoded) throws RegistrySnapshotException {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        try {
            PublicKey key =
                    KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(copy));
            if (!Arrays.equals(copy, key.getEncoded())) {
                throw new RegistrySnapshotException(
                        RegistrySnapshotException.Code.INVALID_PUBLIC_KEY,
                        "registry public key encoding is not canonical");
            }
            return key;
        } catch (RegistrySnapshotException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_PUBLIC_KEY,
                    "registry public key is not a valid Ed25519 key",
                    exception);
        }
    }

    static byte[] sha256(byte[] value) throws RegistrySnapshotException {
        Objects.requireNonNull(value, "value");
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.CRYPTOGRAPHY_FAILURE,
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    static boolean verify(PublicKey key, byte[] message, byte[] signatureBytes)
            throws RegistrySnapshotException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(signatureBytes, "signatureBytes");
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(message);
            return signature.verify(signatureBytes);
        } catch (SignatureException exception) {
            return false;
        } catch (GeneralSecurityException exception) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.CRYPTOGRAPHY_FAILURE,
                    "registry signature verification failed internally",
                    exception);
        }
    }

    static String base32(byte[] value) {
        Objects.requireNonNull(value, "value");
        StringBuilder encoded = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte element : value) {
            buffer = (buffer << 8) | (element & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                encoded.append(BASE32[(buffer >>> bits) & 0x1f]);
            }
        }
        if (bits > 0) {
            encoded.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return encoded.toString();
    }
}
