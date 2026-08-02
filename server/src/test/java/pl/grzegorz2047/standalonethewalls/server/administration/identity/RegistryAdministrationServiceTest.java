package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.registry.AtomicRegistrySnapshotStore;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

class RegistryAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @Test
    void verifyReturnsSafeSummaryWithoutActivatingTheSnapshot() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact artifact = artifact(root, 7L, NOW);
        Fixture fixture = fixture(root, () -> artifact);

        RegistryAdministrationResult result = fixture.administration().verifySnapshot();

        assertThat(result.code()).isEqualTo(RegistryAdministrationResultCode.VERIFIED);
        RegistrySnapshotSummary summary = result.snapshot().orElseThrow();
        assertThat(summary.sequence()).isEqualTo(7L);
        assertThat(summary.generatedAt()).isEqualTo(NOW);
        assertThat(summary.rootKeyId())
                .isEqualTo(RegistryRootId.fromPublicKey(root.getPublic().getEncoded()));
        assertThat(summary.sha256()).hasSize(64);
        assertThat(summary.entries()).isZero();
        assertThat(result.rejectionCode()).isEmpty();
        assertThat(fixture.store().active()).isEmpty();
    }

    @Test
    void reloadActivatesThenReportsIdenticalArtifactAsUnchanged() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact artifact = artifact(root, 8L, NOW);
        AtomicInteger providerCalls = new AtomicInteger();
        Fixture fixture =
                fixture(
                        root,
                        () -> {
                            providerCalls.incrementAndGet();
                            return artifact;
                        });

        RegistryAdministrationResult activated = fixture.administration().reloadRegistry();
        RegistryAdministrationResult unchanged = fixture.administration().reloadRegistry();

        assertThat(activated.code()).isEqualTo(RegistryAdministrationResultCode.ACTIVATED);
        assertThat(unchanged.code()).isEqualTo(RegistryAdministrationResultCode.UNCHANGED);
        assertThat(unchanged.snapshot()).isEqualTo(activated.snapshot());
        assertThat(fixture.store().active()).isPresent();
        assertThat(providerCalls).hasValue(2);
    }

    @Test
    void providerFailureDoesNotExposeItsMessageOrReplaceTheActiveSnapshot() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact activeArtifact = artifact(root, 9L, NOW);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService snapshots = snapshots(root, store);
        snapshots.refresh(() -> activeArtifact);
        VerifiedRegistrySnapshot active = store.active().orElseThrow();
        RegistryAdministrationService administration =
                new RegistryAdministrationService(
                        snapshots,
                        () -> {
                            throw new RegistrySnapshotProviderException("secret mirror detail");
                        });

        RegistryAdministrationResult result = administration.reloadRegistry();

        assertThat(result).isEqualTo(RegistryAdministrationResult.providerFailure());
        assertThat(result.toString()).doesNotContain("secret mirror detail");
        assertThat(store.active()).containsSame(active);
    }

    @Test
    void invalidSignatureIsRejectedWithoutReplacingTheActiveSnapshot() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact activeArtifact = artifact(root, 10L, NOW.minusSeconds(1));
        RegistrySnapshotArtifact candidate = artifact(root, 11L, NOW);
        byte[] damagedSignature = candidate.signature();
        damagedSignature[0] ^= 1;
        RegistrySnapshotArtifact invalid =
                new RegistrySnapshotArtifact(
                        candidate.canonicalJson(), candidate.digest(), damagedSignature);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService snapshots = snapshots(root, store);
        snapshots.refresh(() -> activeArtifact);
        VerifiedRegistrySnapshot active = store.active().orElseThrow();

        RegistryAdministrationResult result =
                new RegistryAdministrationService(snapshots, () -> invalid).reloadRegistry();

        assertThat(result.code()).isEqualTo(RegistryAdministrationResultCode.SNAPSHOT_REJECTED);
        assertThat(result.rejectionCode())
                .contains(RegistrySnapshotException.Code.INVALID_SIGNATURE);
        assertThat(store.active()).containsSame(active);
    }

    @Test
    void rollbackAndEquivocationAreReportedWithoutReplacingLastKnownGood() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact activeArtifact = artifact(root, 12L, NOW);
        RegistrySnapshotArtifact rollbackArtifact = artifact(root, 11L, NOW.minusSeconds(1));
        RegistrySnapshotArtifact equivocationArtifact = artifact(root, 12L, NOW.minusSeconds(1));
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService snapshots = snapshots(root, store);
        snapshots.refresh(() -> activeArtifact);
        VerifiedRegistrySnapshot active = store.active().orElseThrow();

        RegistryAdministrationResult rollback =
                new RegistryAdministrationService(snapshots, () -> rollbackArtifact)
                        .reloadRegistry();
        RegistryAdministrationResult equivocation =
                new RegistryAdministrationService(snapshots, () -> equivocationArtifact)
                        .reloadRegistry();

        assertThat(rollback.rejectionCode()).contains(RegistrySnapshotException.Code.ROLLBACK);
        assertThat(equivocation.rejectionCode())
                .contains(RegistrySnapshotException.Code.EQUIVOCATION);
        assertThat(store.active()).containsSame(active);
    }

    private static Fixture fixture(KeyPair root, RegistrySnapshotProvider provider)
            throws Exception {
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService snapshots = snapshots(root, store);
        return new Fixture(store, new RegistryAdministrationService(snapshots, provider));
    }

    private static RegistrySnapshotService snapshots(
            KeyPair root, AtomicRegistrySnapshotStore store) throws RegistrySnapshotException {
        return new RegistrySnapshotService(
                new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC)),
                RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                RegistrySnapshotPolicy.DEFAULT,
                store);
    }

    private static KeyPair root() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static RegistrySnapshotArtifact artifact(
            KeyPair root, long sequence, Instant generatedAt) throws Exception {
        RegistrySnapshotPayload payload =
                new RegistrySnapshotPayload(
                        sequence,
                        generatedAt,
                        RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                        List.of());
        byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate());
        signer.update(canonicalJson);
        return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
    }

    private record Fixture(
            AtomicRegistrySnapshotStore store, RegistryAdministrationService administration) {}
}
