package pl.grzegorz2047.standalonethewalls.registry.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
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
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.registry.AtomicRegistrySnapshotStore;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;

class RegistrySnapshotCachingRefreshClassificationTest {
    private static final Instant NOW = Instant.parse("2026-08-02T15:00:00Z");

    @TempDir Path temporaryDirectory;

    @Test
    void classifiesActivationUnchangedAndProviderFailure() throws Exception {
        Fixture fixture = new Fixture();
        RegistrySnapshotArtifact artifact = fixture.artifact(1L);
        RegistrySnapshotCachingRefreshService refresh = fixture.refresh("classified.sfrb");

        assertThat(refresh.refreshClassified(() -> artifact))
                .isEqualTo(RegistrySnapshotCachingRefreshService.Outcome.ACTIVATED);
        assertThat(refresh.refreshClassified(() -> artifact))
                .isEqualTo(RegistrySnapshotCachingRefreshService.Outcome.UNCHANGED);
        assertThat(
                        refresh.refreshClassified(
                                () -> {
                                    throw new RegistrySnapshotProviderException("offline");
                                }))
                .isEqualTo(RegistrySnapshotCachingRefreshService.Outcome.PROVIDER_FAILURE);
    }

    @Test
    void classifiesCacheFailureWithoutPublishingCandidate() throws Exception {
        Fixture fixture = new Fixture();
        RegistrySnapshotArtifact baseline = fixture.artifact(4L);
        RegistrySnapshotArtifact candidate = fixture.artifact(5L);
        RegistrySnapshotCachingRefreshService stored = fixture.refresh("stable.sfrb");
        assertThat(stored.refreshClassified(() -> baseline))
                .isEqualTo(RegistrySnapshotCachingRefreshService.Outcome.ACTIVATED);
        AtomicInteger commits = new AtomicInteger();
        RegistrySnapshotCachingRefreshService failing =
                new RegistrySnapshotCachingRefreshService(
                        fixture.snapshots,
                        (artifact, verified) -> {
                            commits.incrementAndGet();
                            throw new RegistrySnapshotProviderException("cache unavailable");
                        });

        assertThat(failing.refreshClassified(() -> candidate))
                .isEqualTo(RegistrySnapshotCachingRefreshService.Outcome.CACHE_FAILURE);
        assertThat(commits).hasValue(1);
        assertThat(fixture.store.active().orElseThrow().sequence()).isEqualTo(4L);
    }

    private final class Fixture {
        private final KeyPair root;
        private final AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        private final RegistrySnapshotService snapshots;

        private Fixture() throws GeneralSecurityException, RegistrySnapshotException {
            root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            snapshots =
                    new RegistrySnapshotService(
                            new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC)),
                            RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                            RegistrySnapshotPolicy.DEFAULT,
                            store);
        }

        private RegistrySnapshotCachingRefreshService refresh(String name) {
            return new RegistrySnapshotCachingRefreshService(
                    snapshots, new RegistrySnapshotBundleFile(temporaryDirectory.resolve(name)));
        }

        private RegistrySnapshotArtifact artifact(long sequence) throws Exception {
            RegistrySnapshotPayload payload =
                    new RegistrySnapshotPayload(
                            sequence,
                            NOW,
                            RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                            List.of());
            byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(root.getPrivate());
            signer.update(canonicalJson);
            return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
        }
    }
}
