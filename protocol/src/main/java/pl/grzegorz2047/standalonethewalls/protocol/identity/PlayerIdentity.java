package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Objects;

/** Application-specific Ed25519 identity. Private material is never included in toString. */
public final class PlayerIdentity {
    private static final byte[] KEY_PAIR_PROBE =
            "SUNDERFRONT-KEY-CHECK-V1".getBytes(StandardCharsets.US_ASCII);

    private final PrivateKey privateKey;
    private final byte[] publicKey;
    private final PlayerId playerId;
    private final PlayerFingerprint fingerprint;

    private PlayerIdentity(KeyPair keyPair) throws IdentityException {
        Objects.requireNonNull(keyPair, "keyPair");
        privateKey = Objects.requireNonNull(keyPair.getPrivate(), "privateKey");
        PublicKey publicKeyObject = Objects.requireNonNull(keyPair.getPublic(), "publicKey");
        requireEd25519(privateKey.getAlgorithm());
        requireEd25519(publicKeyObject.getAlgorithm());
        verifyKeyPair(privateKey, publicKeyObject);
        byte[] encoded = publicKeyObject.getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new IdentityException(
                    IdentityException.Code.KEY_ENCODING_UNAVAILABLE,
                    "public key has no SubjectPublicKeyInfo encoding");
        }
        publicKey = encoded.clone();
        playerId = PlayerId.fromPublicKey(publicKey);
        fingerprint = PlayerFingerprint.fromPublicKey(publicKey);
    }

    public static PlayerIdentity generate(SecureRandom random) throws IdentityException {
        Objects.requireNonNull(random, "random");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            generator.initialize(NamedParameterSpec.ED25519, random);
            return new PlayerIdentity(generator.generateKeyPair());
        } catch (GeneralSecurityException exception) {
            throw new IdentityException(
                    IdentityException.Code.KEY_GENERATION_FAILED,
                    "could not generate Ed25519 identity",
                    exception);
        }
    }

    public static PlayerIdentity loadOrCreate(
            PlayerIdentityStore store, SecureRandom random) throws IdentityException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(random, "random");
        var loaded = store.load();
        if (loaded.isPresent()) {
            return new PlayerIdentity(loaded.orElseThrow());
        }
        PlayerIdentity generated = generate(random);
        store.save(new KeyPair(generated.publicKeyObject(), generated.privateKey));
        return generated;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public PlayerFingerprint fingerprint() {
        return fingerprint;
    }

    public byte[] publicKeyEncoded() {
        return publicKey.clone();
    }

    byte[] sign(byte[] message) throws IdentityException {
        Objects.requireNonNull(message, "message");
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(message);
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new IdentityException(
                    IdentityException.Code.SIGNING_FAILED,
                    "could not sign identity transcript",
                    exception);
        }
    }

    @Override
    public String toString() {
        return "PlayerIdentity[playerId=" + playerId + ", fingerprint=" + fingerprint + ']';
    }

    private PublicKey publicKeyObject() throws IdentityException {
        return IdentityKeys.decodePublicKey(publicKey);
    }

    private static void verifyKeyPair(PrivateKey privateKey, PublicKey publicKey)
            throws IdentityException {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(KEY_PAIR_PROBE);
            byte[] signed = signer.sign();

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(KEY_PAIR_PROBE);
            if (!verifier.verify(signed)) {
                throw new IdentityException(
                        IdentityException.Code.INVALID_KEY_PAIR,
                        "public and private keys do not form one Ed25519 pair");
            }
        } catch (IdentityException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IdentityException(
                    IdentityException.Code.INVALID_KEY_PAIR,
                    "could not validate Ed25519 key pair",
                    exception);
        }
    }

    private static void requireEd25519(String algorithm) throws IdentityException {
        if (!algorithm.equalsIgnoreCase("Ed25519") && !algorithm.equalsIgnoreCase("EdDSA")) {
            throw new IdentityException(
                    IdentityException.Code.INVALID_PUBLIC_KEY,
                    "identity key algorithm must be Ed25519");
        }
    }
}
