package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.AutomaticRegistryRefreshResult;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPermission;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshScheduleConfiguration;

class LocalIdentityRuntimeRegistryRefreshTest {
    private static final Instant NOW = Instant.parse("2026-08-02T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final IdentityAdministrationPrincipal REGISTRY_ADMINISTRATOR =
            new IdentityAdministrationPrincipal(
                    new LocalIdentityAdministratorId("test-registry-admin"),
                    EnumSet.of(IdentityAdministrationPermission.MANAGE_REGISTRY));

    @TempDir Path temporaryDirectory;

    @Test
    void localBundleNeverConstructsRemoteProviderSchedulerOrNetworkAttempt() throws Exception {
        KeyPair root = root();
        AtomicInteger providerConstructions = new AtomicInteger();
        AtomicInteger schedulerConstructions = new AtomicInteger();
        RegistryRefreshConfiguration.LocalBundle local =
                new RegistryRefreshConfiguration.LocalBundle();
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration("local", local),
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        local,
                        CLOCK,
                        ignored -> {
                            providerConstructions.incrementAndGet();
                            throw new AssertionError(
                                    "LOCAL_BUNDLE cannot construct HTTPS provider");
                        });

        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(
                        () -> {
                            schedulerConstructions.incrementAndGet();
                            return new ManualTaskScheduler();
                        },
                        maximum -> 0L);

        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.DISABLED);
        assertThat(providerConstructions).hasValue(0);
        assertThat(schedulerConstructions).hasValue(0);
        scheduler.close();
    }

    @Test
    void disabledHttpsSchedulerCreatesNoExecutorAndPerformsNoRemoteIo() throws Exception {
        KeyPair root = root();
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger schedulerConstructions = new AtomicInteger();
        RegistryRefreshConfiguration.Https https = https(false);
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration("disabled", https),
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored ->
                                () -> {
                                    providerCalls.incrementAndGet();
                                    throw new RegistrySnapshotProviderException(
                                            "unexpected network");
                                });

        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(
                        () -> {
                            schedulerConstructions.incrementAndGet();
                            return new ManualTaskScheduler();
                        },
                        maximum -> 0L);

        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.DISABLED);
        assertThat(providerCalls).hasValue(0);
        assertThat(schedulerConstructions).hasValue(0);
        scheduler.close();
    }

    @Test
    void automaticActivationUnchangedAndOfflineRestartUseLastKnownGoodBundle() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact artifact = artifact(root, 7L, NOW);
        AtomicInteger remoteCalls = new AtomicInteger();
        RegistryRefreshConfiguration.Https https = https(true);
        LocalIdentityRuntimeConfiguration configuration = configuration("restore", https);
        LocalIdentityRuntime first =
                LocalIdentityRuntime.open(
                        configuration,
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored ->
                                () -> {
                                    remoteCalls.incrementAndGet();
                                    return artifact;
                                });
        ManualTaskScheduler manual = new ManualTaskScheduler();
        RegistryRefreshScheduler scheduler =
                first.startAutomaticRegistryRefresh(() -> manual, maximum -> 0L);

        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.ACTIVATED);
        assertThat(first.registryAvailability().requireSnapshot().sequence()).isEqualTo(7L);
        assertThat(configuration.registryBundlePath()).isRegularFile();
        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.UNCHANGED);
        scheduler.close();
        assertThat(remoteCalls).hasValue(2);

        AtomicInteger offlineCalls = new AtomicInteger();
        LocalIdentityRuntime restarted =
                LocalIdentityRuntime.open(
                        configuration,
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored ->
                                () -> {
                                    offlineCalls.incrementAndGet();
                                    throw new RegistrySnapshotProviderException("offline");
                                });

        assertThat(restarted.registryAvailability().requireSnapshot().sequence()).isEqualTo(7L);
        assertThat(offlineCalls).hasValue(0);
    }

    @Test
    void providerFailureKeepsActiveSnapshotAndCachedBundle() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact baseline = artifact(root, 8L, NOW.minusSeconds(1));
        AtomicInteger calls = new AtomicInteger();
        RegistryRefreshConfiguration.Https https = https(true);
        LocalIdentityRuntimeConfiguration configuration = configuration("provider-failure", https);
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration,
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored ->
                                () -> {
                                    if (calls.getAndIncrement() == 0) {
                                        return baseline;
                                    }
                                    throw new RegistrySnapshotProviderException("offline");
                                });
        ManualTaskScheduler manual = new ManualTaskScheduler();
        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(() -> manual, maximum -> 0L);

        manual.runNext();
        manual.runNext();

        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.PROVIDER_FAILURE);
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(8L);
        assertThat(configuration.registryBundlePath()).isRegularFile();
        scheduler.close();
    }

    @Test
    void invalidSignatureRollbackAndEquivocationKeepActiveSnapshotAndRetry() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact baseline = artifact(root, 10L, NOW.minusSeconds(2));
        RegistrySnapshotArtifact invalid = invalidSignature(artifact(root, 11L, NOW));
        RegistrySnapshotArtifact rollback = artifact(root, 9L, NOW.minusSeconds(3));
        RegistrySnapshotArtifact equivocation = artifact(root, 10L, NOW.minusSeconds(1));
        Deque<RegistrySnapshotArtifact> artifacts =
                new ArrayDeque<>(List.of(baseline, invalid, rollback, equivocation));
        RegistryRefreshConfiguration.Https https = https(true);
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration("rejections", https),
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored -> artifacts::removeFirst);
        ManualTaskScheduler manual = new ManualTaskScheduler();
        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(() -> manual, maximum -> 0L);

        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.ACTIVATED);
        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.SNAPSHOT_REJECTED);
        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.ROLLBACK_REJECTED);
        manual.runNext();
        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.EQUIVOCATION_REJECTED);
        assertThat(runtime.registryAvailability().requireSnapshot().sequence()).isEqualTo(10L);
        assertThat(scheduler.status().consecutiveFailures()).isEqualTo(3);
        scheduler.close();
    }

    @Test
    void cacheFailureIsClassifiedAndDoesNotPublishCandidate() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact candidate = artifact(root, 1L, NOW);
        RegistryRefreshConfiguration.Https https = https(true);
        Path bundlePath = temporaryDirectory.resolve("cache-directory.sfrb");
        Files.createDirectory(bundlePath);
        LocalIdentityRuntimeConfiguration configuration =
                new LocalIdentityRuntimeConfiguration(
                        temporaryDirectory.resolve("cache.sqlite"),
                        bundlePath,
                        HandleAuthorizationMode.GLOBAL_ONLY,
                        https);
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration,
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored -> () -> candidate);
        ManualTaskScheduler manual = new ManualTaskScheduler();
        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(() -> manual, maximum -> 0L);

        manual.runNext();

        assertThat(scheduler.status().lastResult())
                .contains(AutomaticRegistryRefreshResult.CACHE_FAILURE);
        assertThat(runtime.registryAvailability().snapshot()).isEmpty();
        assertThat(bundlePath).isDirectory();
        scheduler.close();
    }

    @Test
    void automaticAndManualRefreshShareOneSingleFlightProviderBoundary() throws Exception {
        KeyPair root = root();
        RegistrySnapshotArtifact artifact = artifact(root, 2L, NOW);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        RegistryRefreshConfiguration.Https https = https(true);
        LocalIdentityRuntime runtime =
                LocalIdentityRuntime.open(
                        configuration("single-flight", https),
                        trustBundle(root),
                        RegistrySnapshotPolicy.DEFAULT,
                        https,
                        CLOCK,
                        ignored ->
                                () -> {
                                    int call = calls.incrementAndGet();
                                    int current = active.incrementAndGet();
                                    maximumActive.accumulateAndGet(current, Math::max);
                                    if (call == 1) {
                                        firstEntered.countDown();
                                        await(releaseFirst);
                                    }
                                    active.decrementAndGet();
                                    return artifact;
                                });
        ManualTaskScheduler manual = new ManualTaskScheduler();
        RegistryRefreshScheduler scheduler =
                runtime.startAutomaticRegistryRefresh(() -> manual, maximum -> 0L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> automatic = executor.submit(manual::runNext);
            assertThat(firstEntered.await(5L, TimeUnit.SECONDS)).isTrue();
            Future<?> manualCommand =
                    executor.submit(
                            () ->
                                    runtime.execute(
                                            new IdentityAdministrationCommand.VerifySnapshot(),
                                            REGISTRY_ADMINISTRATOR));
            assertThat(calls).hasValue(1);
            releaseFirst.countDown();
            automatic.get(5L, TimeUnit.SECONDS);
            manualCommand.get(5L, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(calls).hasValue(2);
        assertThat(maximumActive).hasValue(1);
        scheduler.close();
    }

    private LocalIdentityRuntimeConfiguration configuration(
            String name, RegistryRefreshConfiguration refreshConfiguration) {
        return new LocalIdentityRuntimeConfiguration(
                temporaryDirectory.resolve(name + ".sqlite"),
                temporaryDirectory.resolve(name + ".sfrb"),
                HandleAuthorizationMode.GLOBAL_ONLY,
                refreshConfiguration);
    }

    private static RegistryRefreshConfiguration.Https https(boolean enabled) {
        return new RegistryRefreshConfiguration.Https(
                new RegistrySnapshotHttpsConfiguration(
                        java.net.URI.create("https://registry.example/v1/registry.json"),
                        java.net.URI.create("https://registry.example/v1/registry.sha256"),
                        java.net.URI.create("https://registry.example/v1/registry.sig"),
                        4096),
                new RegistryRefreshScheduleConfiguration(
                        enabled,
                        Duration.ZERO,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(16),
                        Duration.ZERO));
    }

    private static RegistryTrustBundle trustBundle(KeyPair root) throws Exception {
        return RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
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

    private static RegistrySnapshotArtifact invalidSignature(RegistrySnapshotArtifact artifact) {
        byte[] signature = artifact.signature();
        signature[0] ^= 1;
        return new RegistrySnapshotArtifact(artifact.canonicalJson(), artifact.digest(), signature);
    }

    private static void await(CountDownLatch latch) throws RegistrySnapshotProviderException {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new RegistrySnapshotProviderException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistrySnapshotProviderException("test latch interrupted", exception);
        }
    }

    private static final class ManualTaskScheduler
            implements RegistryRefreshScheduler.TaskScheduler {
        private final Deque<Entry> entries = new ArrayDeque<>();

        @Override
        public RegistryRefreshScheduler.ScheduledTask schedule(Duration delay, Runnable task) {
            Entry entry = new Entry(task);
            entries.addLast(entry);
            return () -> entry.cancelled = true;
        }

        @Override
        public void close() {
            entries.forEach(entry -> entry.cancelled = true);
        }

        private void runNext() {
            Entry entry = entries.removeFirst();
            if (!entry.cancelled) {
                entry.task.run();
            }
        }

        private static final class Entry {
            private final Runnable task;
            private boolean cancelled;

            private Entry(Runnable task) {
                this.task = task;
            }
        }
    }
}
