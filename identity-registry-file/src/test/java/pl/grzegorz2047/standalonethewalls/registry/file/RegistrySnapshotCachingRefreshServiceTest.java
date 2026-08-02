package pl.grzegorz2047.standalonethewalls.registry.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.registry.AtomicRegistrySnapshotStore;
import pl.grzegorz2047.standalonethewalls.registry.RegistryActivationResult;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

class RegistrySnapshotCachingRefreshServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");

    @TempDir Path temporaryDirectory;

    @Test
    void firstAndHigherSequencePersistExactArtifactBeforeActivationAndReopen()
            throws RegistrySnapshotException, RegistrySnapshotProviderException {
        FixtureFactory fixtures = new FixtureFactory();
        RegistrySnapshotArtifact first = fixtures.artifact(1L, "player_one");
        RegistrySnapshotArtifact second = fixtures.artifact(2L, "player_two");
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotBundleFile bundle = bundle("registry.sfrb");
        RegistrySnapshotCachingRefreshService refresh = refreshService(fixtures, store, bundle);

        assertThat(refresh.refresh(() -> first)).isEqualTo(RegistryActivationResult.ACTIVATED);
        assertArtifact(bundle.load(), first);
        assertThat(store.active().orElseThrow().sequence()).isEqualTo(1L);

        assertThat(refresh.refresh(() -> second)).isEqualTo(RegistryActivationResult.ACTIVATED);
        assertArtifact(bundle.load(), second);
        assertThat(store.active().orElseThrow().sequence()).isEqualTo(2L);

        RegistrySnapshotBundleFile reopened = new RegistrySnapshotBundleFile(bundle.path());
        assertArtifact(reopened.load(), second);
    }

    @Test
    void unchangedRollbackEquivocationAndInvalidSignatureDoNotRewriteBundle()
            throws IOException, RegistrySnapshotException, RegistrySnapshotProviderException {
        FixtureFactory fixtures = new FixtureFactory();
        RegistrySnapshotArtifact active = fixtures.artifact(10L, "player_one");
        RegistrySnapshotArtifact rollback = fixtures.artifact(9L, "player_one");
        RegistrySnapshotArtifact equivocation = fixtures.artifact(10L, "player_two");
        byte[] invalidSignature = active.signature();
        invalidSignature[0] ^= 1;
        RegistrySnapshotArtifact invalid =
                new RegistrySnapshotArtifact(
                        active.canonicalJson(), active.digest(), invalidSignature);
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotBundleFile bundle = bundle("stable.sfrb");
        RegistrySnapshotCachingRefreshService refresh = refreshService(fixtures, store, bundle);
        refresh.refresh(() -> active);
        Files.setLastModifiedTime(
                bundle.path(), FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));
        FileTime marker = Files.getLastModifiedTime(bundle.path());

        assertThat(refresh.refresh(() -> active)).isEqualTo(RegistryActivationResult.UNCHANGED);
        assertCode(refresh, rollback, RegistrySnapshotException.Code.ROLLBACK);
        assertCode(refresh, equivocation, RegistrySnapshotException.Code.EQUIVOCATION);
        assertThatThrownBy(() -> refresh.refresh(() -> invalid))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.INVALID_SIGNATURE));

        assertThat(Files.getLastModifiedTime(bundle.path())).isEqualTo(marker);
        assertArtifact(bundle.load(), active);
        assertThat(store.active().orElseThrow().sequence()).isEqualTo(10L);
    }

    @Test
    void forcedCacheFailurePreservesActiveAndPreviousBundle()
            throws RegistrySnapshotException, RegistrySnapshotProviderException {
        FixtureFactory fixtures = new FixtureFactory();
        RegistrySnapshotArtifact first = fixtures.artifact(20L, "player_one");
        RegistrySnapshotArtifact candidate = fixtures.artifact(21L, "player_two");
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotBundleFile bundle = bundle("failure.sfrb");
        RegistrySnapshotService snapshotService = fixtures.service(store);
        new RegistrySnapshotCachingRefreshService(snapshotService, bundle).refresh(() -> first);
        VerifiedRegistrySnapshot previous = store.active().orElseThrow();
        AtomicInteger commits = new AtomicInteger();
        RegistrySnapshotCachingRefreshService failing =
                new RegistrySnapshotCachingRefreshService(
                        snapshotService,
                        (artifact, verifiedSnapshot) -> {
                            commits.incrementAndGet();
                            throw new RegistrySnapshotProviderException("forced cache failure");
                        });

        assertThatThrownBy(() -> failing.refresh(() -> candidate))
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("forced cache failure");

        assertThat(commits).hasValue(1);
        assertThat(store.active()).containsSame(previous);
        assertArtifact(bundle.load(), first);
    }

    @Test
    void olderConcurrentFetchCannotOverwriteNewerCacheAfterNewerActivation()
            throws InterruptedException,
                    ExecutionException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        FixtureFactory fixtures = new FixtureFactory();
        RegistrySnapshotArtifact baseline = fixtures.artifact(30L, "player_one");
        RegistrySnapshotArtifact older = fixtures.artifact(31L, "player_two");
        RegistrySnapshotArtifact newer = fixtures.artifact(32L, "player_three");
        AtomicRegistrySnapshotStore store = new AtomicRegistrySnapshotStore();
        RegistrySnapshotBundleFile bundle = bundle("concurrent.sfrb");
        RegistrySnapshotCachingRefreshService refresh = refreshService(fixtures, store, bundle);
        refresh.refresh(() -> baseline);
        CountDownLatch olderStarted = new CountDownLatch(1);
        CountDownLatch releaseOlder = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<RegistryActivationResult> olderResult =
                executor.submit(
                        () ->
                                refresh.refresh(
                                        () -> {
                                            olderStarted.countDown();
                                            awaitProviderRelease(releaseOlder);
                                            return older;
                                        }));
        try {
            assertThat(olderStarted.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(refresh.refresh(() -> newer)).isEqualTo(RegistryActivationResult.ACTIVATED);
            releaseOlder.countDown();

            assertThatThrownBy(olderResult::get)
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            failure ->
                                    assertThat(failure.getCause())
                                            .isInstanceOfSatisfying(
                                                    RegistrySnapshotException.class,
                                                    snapshotFailure ->
                                                            assertThat(snapshotFailure.code())
                                                                    .isEqualTo(
                                                                            RegistrySnapshotException
                                                                                    .Code
                                                                                    .ROLLBACK)));
        } finally {
            releaseOlder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(store.active().orElseThrow().sequence()).isEqualTo(32L);
        assertArtifact(bundle.load(), newer);
    }

    private RegistrySnapshotBundleFile bundle(String name) {
        return new RegistrySnapshotBundleFile(temporaryDirectory.resolve(name));
    }

    private static RegistrySnapshotCachingRefreshService refreshService(
            FixtureFactory fixtures,
            AtomicRegistrySnapshotStore store,
            RegistrySnapshotBundleFile bundle)
            throws RegistrySnapshotException {
        return new RegistrySnapshotCachingRefreshService(fixtures.service(store), bundle);
    }

    private static void assertCode(
            RegistrySnapshotCachingRefreshService refresh,
            RegistrySnapshotArtifact artifact,
            RegistrySnapshotException.Code expected) {
        assertThatThrownBy(() -> refresh.refresh(() -> artifact))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }

    private static void assertArtifact(
            RegistrySnapshotArtifact actual, RegistrySnapshotArtifact expected) {
        assertThat(actual.canonicalJson()).containsExactly(expected.canonicalJson());
        assertThat(actual.digest()).containsExactly(expected.digest());
        assertThat(actual.signature()).containsExactly(expected.signature());
    }

    private static void awaitProviderRelease(CountDownLatch release)
            throws RegistrySnapshotProviderException {
        try {
            if (!release.await(5L, TimeUnit.SECONDS)) {
                throw new RegistrySnapshotProviderException("test provider release timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistrySnapshotProviderException(
                    "test provider interrupted while awaiting release", exception);
        }
    }

    private static final class FixtureFactory {
        private final KeyPair root;
        private final PlayerIdentity player;

        private FixtureFactory() {
            try {
                root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                player = PlayerIdentity.generate(new SecureRandom());
            } catch (GeneralSecurityException | IdentityException exception) {
                throw new AssertionError("could not create registry test identities", exception);
            }
        }

        private RegistrySnapshotArtifact artifact(long sequence, String handle) {
            try {
                RegistrySnapshotEntry entry =
                        RegistrySnapshotEntry.create(
                                new CanonicalHandle(handle),
                                player.playerId(),
                                player.publicKeyEncoded(),
                                RegistryEntryStatus.ACTIVE);
                RegistrySnapshotPayload payload =
                        new RegistrySnapshotPayload(
                                sequence,
                                NOW,
                                RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                                List.of(entry));
                byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
                Signature signer = Signature.getInstance("Ed25519");
                signer.initSign(root.getPrivate());
                signer.update(canonicalJson);
                return new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
            } catch (GeneralSecurityException | RegistrySnapshotException exception) {
                throw new AssertionError("could not create signed registry artifact", exception);
            }
        }

        private RegistrySnapshotService service(AtomicRegistrySnapshotStore store)
                throws RegistrySnapshotException {
            return new RegistrySnapshotService(
                    new RegistrySnapshotVerifier(Clock.fixed(NOW, ZoneOffset.UTC)),
                    RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                    RegistrySnapshotPolicy.DEFAULT,
                    store);
        }
    }
}
