package pl.grzegorz2047.standalonethewalls.registry;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.List;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

final class RegistryTestFixtures {
    private RegistryTestFixtures() {
        throw new AssertionError("No instances");
    }

    static KeyPair rootKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        generator.initialize(NamedParameterSpec.ED25519, new SecureRandom());
        return generator.generateKeyPair();
    }

    static PlayerIdentity playerIdentity() throws IdentityException {
        return PlayerIdentity.generate(new SecureRandom());
    }

    static RegistrySnapshotPayload payload(
            KeyPair root,
            long sequence,
            Instant generatedAt,
            String handle,
            PlayerIdentity player,
            RegistryEntryStatus status)
            throws RegistrySnapshotException {
        RegistrySnapshotEntry entry =
                RegistrySnapshotEntry.create(
                        new CanonicalHandle(handle),
                        player.playerId(),
                        player.publicKeyEncoded(),
                        status);
        return new RegistrySnapshotPayload(
                sequence,
                generatedAt,
                RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                List.of(entry));
    }

    static RegistrySnapshotArtifact sign(RegistrySnapshotPayload payload, KeyPair root)
            throws RegistrySnapshotException, GeneralSecurityException {
        byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate());
        signer.update(canonicalJson);
        return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
    }
}
