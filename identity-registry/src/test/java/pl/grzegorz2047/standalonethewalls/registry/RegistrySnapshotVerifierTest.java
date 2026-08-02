package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;

class RegistrySnapshotVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-02T05:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void verifiesCanonicalDigestSignatureRootAndEntries()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistrySnapshotPayload payload =
                RegistryTestFixtures.payload(
                        root,
                        42L,
                        NOW.minus(Duration.ofHours(1)),
                        "player_one",
                        player,
                        RegistryEntryStatus.ACTIVE);
        RegistrySnapshotArtifact artifact = RegistryTestFixtures.sign(payload, root);
        RegistryTrustBundle trust = RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));

        VerifiedRegistrySnapshot verified =
                new RegistrySnapshotVerifier(CLOCK)
                        .verify(artifact, trust, RegistrySnapshotPolicy.DEFAULT);

        assertThat(verified.sequence()).isEqualTo(42L);
        assertThat(verified.generatedAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        assertThat(verified.rootKeyId()).isEqualTo(payload.rootKeyId());
        assertThat(verified.digest()).containsExactly(artifact.digest());
        assertThat(verified.find(payload.entries().getFirst().handle()))
                .contains(payload.entries().getFirst());
        assertThat(verified.toString()).doesNotContain(Arrays.toString(artifact.digest()));
    }

    @Test
    void rejectsDigestSignatureAndUnknownRootWithoutLeakingBytes()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        KeyPair otherRoot = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistrySnapshotPayload payload =
                RegistryTestFixtures.payload(
                        root, 1L, NOW, "player_one", player, RegistryEntryStatus.ACTIVE);
        RegistrySnapshotArtifact valid = RegistryTestFixtures.sign(payload, root);
        byte[] wrongDigest = valid.digest();
        wrongDigest[0] ^= 1;
        byte[] wrongSignature = valid.signature();
        wrongSignature[0] ^= 1;
        RegistrySnapshotVerifier verifier = new RegistrySnapshotVerifier(CLOCK);
        RegistryTrustBundle trustedRoot =
                RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
        RegistryTrustBundle unknownRoot =
                RegistryTrustBundle.of(List.of(otherRoot.getPublic().getEncoded()));

        assertCode(
                verifier,
                new RegistrySnapshotArtifact(valid.canonicalJson(), wrongDigest, valid.signature()),
                trustedRoot,
                RegistrySnapshotException.Code.DIGEST_MISMATCH);
        assertCode(
                verifier,
                new RegistrySnapshotArtifact(valid.canonicalJson(), valid.digest(), wrongSignature),
                trustedRoot,
                RegistrySnapshotException.Code.INVALID_SIGNATURE);
        assertCode(verifier, valid, unknownRoot, RegistrySnapshotException.Code.UNKNOWN_ROOT_KEY);
    }

    @Test
    void enforcesMinimumSequenceAgeFutureSkewSizeAndEntryLimit()
            throws GeneralSecurityException, IdentityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();
        PlayerIdentity player = RegistryTestFixtures.playerIdentity();
        RegistryTrustBundle trust = RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
        RegistrySnapshotVerifier verifier = new RegistrySnapshotVerifier(CLOCK);

        RegistrySnapshotPolicy minimumSequence =
                new RegistrySnapshotPolicy(
                        10L, Duration.ofDays(30), Duration.ofMinutes(5), 1_000_000, 10);
        assertPolicyCode(
                verifier,
                root,
                player,
                9L,
                NOW,
                trust,
                minimumSequence,
                RegistrySnapshotException.Code.BELOW_MINIMUM_SEQUENCE);

        RegistrySnapshotPolicy freshness =
                new RegistrySnapshotPolicy(
                        0L, Duration.ofDays(1), Duration.ofMinutes(5), 1_000_000, 10);
        assertPolicyCode(
                verifier,
                root,
                player,
                10L,
                NOW.minus(Duration.ofDays(1).plusSeconds(1)),
                trust,
                freshness,
                RegistrySnapshotException.Code.SNAPSHOT_TOO_OLD);
        assertPolicyCode(
                verifier,
                root,
                player,
                10L,
                NOW.plus(Duration.ofMinutes(5).plusSeconds(1)),
                trust,
                freshness,
                RegistrySnapshotException.Code.SNAPSHOT_FROM_FUTURE);

        RegistrySnapshotArtifact valid =
                RegistryTestFixtures.sign(
                        RegistryTestFixtures.payload(
                                root, 10L, NOW, "player_one", player, RegistryEntryStatus.ACTIVE),
                        root);
        RegistrySnapshotPolicy tooSmall =
                new RegistrySnapshotPolicy(
                        0L,
                        Duration.ofDays(1),
                        Duration.ofMinutes(5),
                        valid.canonicalJson().length - 1,
                        10);
        assertCode(
                verifier,
                valid,
                trust,
                tooSmall,
                RegistrySnapshotException.Code.INVALID_ARTIFACT_SIZE);

        RegistrySnapshotPolicy noEntries =
                new RegistrySnapshotPolicy(
                        0L, Duration.ofDays(1), Duration.ofMinutes(5), 1_000_000, 0);
        assertCode(
                verifier, valid, trust, noEntries, RegistrySnapshotException.Code.TOO_MANY_ENTRIES);
    }

    @Test
    void trustBundleRejectsDuplicateAndNonEd25519Roots()
            throws GeneralSecurityException, RegistrySnapshotException {
        KeyPair root = RegistryTestFixtures.rootKeyPair();

        assertThatThrownBy(
                        () ->
                                RegistryTrustBundle.of(
                                        List.of(
                                                root.getPublic().getEncoded(),
                                                root.getPublic().getEncoded())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistryTrustBundle.of(List.of(new byte[] {1, 2, 3, 4})))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.INVALID_PUBLIC_KEY));
    }

    private static void assertPolicyCode(
            RegistrySnapshotVerifier verifier,
            KeyPair root,
            PlayerIdentity player,
            long sequence,
            Instant generatedAt,
            RegistryTrustBundle trust,
            RegistrySnapshotPolicy policy,
            RegistrySnapshotException.Code expected)
            throws RegistrySnapshotException, GeneralSecurityException {
        RegistrySnapshotArtifact artifact =
                RegistryTestFixtures.sign(
                        RegistryTestFixtures.payload(
                                root,
                                sequence,
                                generatedAt,
                                "player_one",
                                player,
                                RegistryEntryStatus.ACTIVE),
                        root);
        assertCode(verifier, artifact, trust, policy, expected);
    }

    private static void assertCode(
            RegistrySnapshotVerifier verifier,
            RegistrySnapshotArtifact artifact,
            RegistryTrustBundle trust,
            RegistrySnapshotException.Code expected) {
        assertCode(verifier, artifact, trust, RegistrySnapshotPolicy.DEFAULT, expected);
    }

    private static void assertCode(
            RegistrySnapshotVerifier verifier,
            RegistrySnapshotArtifact artifact,
            RegistryTrustBundle trust,
            RegistrySnapshotPolicy policy,
            RegistrySnapshotException.Code expected) {
        assertThatThrownBy(() -> verifier.verify(artifact, trust, policy))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }
}
