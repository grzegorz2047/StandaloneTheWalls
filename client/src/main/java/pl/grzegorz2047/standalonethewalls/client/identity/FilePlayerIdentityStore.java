package pl.grzegorz2047.standalonethewalls.client.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentityStore;

/** Strict, application-specific Ed25519 identity file with atomic first-use activation. */
public final class FilePlayerIdentityStore implements PlayerIdentityStore {
    private static final int MAGIC = 0x53464B49;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_KEY_BYTES = 4_096;
    private static final int MAXIMUM_FILE_BYTES = 8_256;
    private static final byte[] KEY_PAIR_PROBE =
            "SUNDERFRONT-FILE-KEY-CHECK-V1".getBytes(StandardCharsets.US_ASCII);

    private final Path path;

    public FilePlayerIdentityStore(Path path) {
        this.path = SecureAtomicFile.requireAbsoluteFile(path, "path");
    }

    public Path path() {
        return path;
    }

    @Override
    public Optional<KeyPair> load() throws IdentityException {
        try {
            Optional<byte[]> encoded = SecureAtomicFile.readIfPresent(path, MAXIMUM_FILE_BYTES);
            if (encoded.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(decode(encoded.orElseThrow()));
        } catch (IdentityException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_STORE_READ_FAILED,
                    "could not read the player identity store",
                    exception);
        }
    }

    @Override
    public void save(KeyPair keyPair) throws IdentityException {
        Objects.requireNonNull(keyPair, "keyPair");
        byte[] encoded = encode(keyPair);
        try {
            SecureAtomicFile.withExclusiveLock(
                    path,
                    () -> {
                        Optional<byte[]> existingBytes =
                                SecureAtomicFile.readIfPresent(path, MAXIMUM_FILE_BYTES);
                        if (existingBytes.isPresent()) {
                            KeyPair existing;
                            try {
                                existing = decode(existingBytes.orElseThrow());
                            } catch (IdentityException exception) {
                                throw new InvalidExistingIdentityException(exception);
                            }
                            if (sameKeyPair(existing, keyPair)) {
                                return null;
                            }
                            throw new IdentityConflictException();
                        }
                        SecureAtomicFile.replaceAtomically(path, encoded);
                        return null;
                    });
        } catch (IdentityConflictException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_STORE_CONFLICT,
                    "another process created a different player identity first",
                    exception);
        } catch (InvalidExistingIdentityException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_STORE_INVALID,
                    "the existing player identity store is invalid",
                    exception.getCause());
        } catch (IOException | RuntimeException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_STORE_WRITE_FAILED,
                    "could not write the player identity store",
                    exception);
        }
    }

    private static byte[] encode(KeyPair keyPair) throws IdentityException {
        PublicKey publicKey = Objects.requireNonNull(keyPair.getPublic(), "publicKey");
        PrivateKey privateKey = Objects.requireNonNull(keyPair.getPrivate(), "privateKey");
        requireEd25519(publicKey.getAlgorithm());
        requireEd25519(privateKey.getAlgorithm());
        byte[] privateEncoded = requireEncoded(privateKey.getEncoded(), "private key");
        byte[] publicEncoded = requireEncoded(publicKey.getEncoded(), "public key");
        if (privateEncoded.length > MAXIMUM_KEY_BYTES || publicEncoded.length > MAXIMUM_KEY_BYTES) {
            throw invalidStore("identity key encoding exceeds the accepted bound", null);
        }
        KeyPair canonical = decodeKeys(privateEncoded, publicEncoded);
        verifyKeyPair(canonical.getPrivate(), canonical.getPublic());

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(SCHEMA_VERSION);
                output.writeInt(privateEncoded.length);
                output.writeInt(publicEncoded.length);
                output.write(privateEncoded);
                output.write(publicEncoded);
            }
            byte[] result = bytes.toByteArray();
            if (result.length > MAXIMUM_FILE_BYTES) {
                throw invalidStore("identity file exceeds the accepted bound", null);
            }
            return result;
        } catch (IOException exception) {
            throw new AssertionError("in-memory identity encoding failed", exception);
        }
    }

    private static KeyPair decode(byte[] encoded) throws IdentityException {
        try (DataInputStream input =
                new DataInputStream(new ByteArrayInputStream(Objects.requireNonNull(encoded)))) {
            if (input.readInt() != MAGIC) {
                throw invalidStore("identity file magic is invalid", null);
            }
            if (input.readInt() != SCHEMA_VERSION) {
                throw invalidStore("identity file schema is unsupported", null);
            }
            int privateLength = input.readInt();
            int publicLength = input.readInt();
            requireLength(privateLength);
            requireLength(publicLength);
            byte[] privateEncoded = input.readNBytes(privateLength);
            byte[] publicEncoded = input.readNBytes(publicLength);
            if (privateEncoded.length != privateLength || publicEncoded.length != publicLength) {
                throw invalidStore("identity file is truncated", null);
            }
            if (input.read() != -1) {
                throw invalidStore("identity file contains trailing bytes", null);
            }
            KeyPair keyPair = decodeKeys(privateEncoded, publicEncoded);
            verifyKeyPair(keyPair.getPrivate(), keyPair.getPublic());
            return keyPair;
        } catch (EOFException exception) {
            throw invalidStore("identity file is truncated", exception);
        } catch (IOException exception) {
            throw invalidStore("identity file could not be decoded", exception);
        }
    }

    private static KeyPair decodeKeys(byte[] privateEncoded, byte[] publicEncoded)
            throws IdentityException {
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey =
                    factory.generatePrivate(new PKCS8EncodedKeySpec(privateEncoded.clone()));
            PublicKey publicKey =
                    factory.generatePublic(new X509EncodedKeySpec(publicEncoded.clone()));
            requireEd25519(privateKey.getAlgorithm());
            requireEd25519(publicKey.getAlgorithm());
            if (!Arrays.equals(privateEncoded, privateKey.getEncoded())
                    || !Arrays.equals(publicEncoded, publicKey.getEncoded())) {
                throw invalidStore("identity key encoding is not canonical", null);
            }
            return new KeyPair(publicKey, privateKey);
        } catch (IdentityException exception) {
            throw exception;
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw invalidStore("identity key encoding is invalid", exception);
        }
    }

    private static void verifyKeyPair(PrivateKey privateKey, PublicKey publicKey)
            throws IdentityException {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(KEY_PAIR_PROBE);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(KEY_PAIR_PROBE);
            if (!verifier.verify(signature)) {
                throw invalidStore("identity public and private keys do not match", null);
            }
        } catch (IdentityException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw invalidStore("identity key pair could not be validated", exception);
        }
    }

    private static boolean sameKeyPair(KeyPair first, KeyPair second) {
        return Arrays.equals(first.getPrivate().getEncoded(), second.getPrivate().getEncoded())
                && Arrays.equals(first.getPublic().getEncoded(), second.getPublic().getEncoded());
    }

    private static byte[] requireEncoded(byte[] encoded, String field) throws IdentityException {
        if (encoded == null || encoded.length == 0) {
            throw invalidStore(field + " has no canonical encoding", null);
        }
        return encoded.clone();
    }

    private static void requireLength(int length) throws IdentityException {
        if (length < 1 || length > MAXIMUM_KEY_BYTES) {
            throw invalidStore("identity key length is outside the accepted bound", null);
        }
    }

    private static void requireEd25519(String algorithm) throws IdentityException {
        if (!"Ed25519".equalsIgnoreCase(algorithm) && !"EdDSA".equalsIgnoreCase(algorithm)) {
            throw invalidStore("identity key algorithm must be Ed25519", null);
        }
    }

    private static IdentityException invalidStore(String message, Throwable cause) {
        return cause == null
                ? new IdentityException(IdentityException.Code.KEY_STORE_INVALID, message)
                : new IdentityException(IdentityException.Code.KEY_STORE_INVALID, message, cause);
    }

    private static final class IdentityConflictException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class InvalidExistingIdentityException extends IOException {
        private static final long serialVersionUID = 1L;

        private InvalidExistingIdentityException(IdentityException cause) {
            super(cause);
        }
    }
}
