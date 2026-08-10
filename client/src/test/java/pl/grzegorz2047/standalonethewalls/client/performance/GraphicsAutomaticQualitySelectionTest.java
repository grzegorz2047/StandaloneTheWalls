package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsAutomaticQualitySelectionTest {
    private static final String REPOSITORY_COMMIT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir Path tempDirectory;

    @Test
    void compatiblePersistedStateSkipsBenchmarkAndUsesEffectivePreset()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path assetLock = writeAssetLock("current.lock", "{\"packs\":[],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("compatible-data");
        new GraphicsQualityStateStore(dataDirectory)
                .save(
                        new GraphicsQualityState(
                                currentKey,
                                GraphicsQualityPreset.LOW,
                                Optional.of(GraphicsQualityPreset.HIGH)));
        AtomicInteger benchmarkRuns = new AtomicInteger();

        GraphicsQualityPreset selected =
                GraphicsAutomaticQualitySelection.resolve(
                        dataDirectory,
                        assetLock,
                        Optional.of(REPOSITORY_COMMIT),
                        (config, previousState) -> {
                            benchmarkRuns.incrementAndGet();
                            throw new AssertionError("compatible state must skip benchmark");
                        });

        assertThat(selected).isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(benchmarkRuns).hasValue(0);
    }

    @Test
    void missingStateRunsOncePersistsOutcomeAndUsesFixedProfile()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path assetLock = writeAssetLock("missing.lock", "{\"packs\":[1],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("missing-data");
        AtomicInteger benchmarkRuns = new AtomicInteger();
        AtomicReference<GraphicsBenchmarkSession.Config> observedConfig = new AtomicReference<>();

        GraphicsQualityPreset selected =
                GraphicsAutomaticQualitySelection.resolve(
                        dataDirectory,
                        assetLock,
                        Optional.of(REPOSITORY_COMMIT),
                        (config, previousState) -> {
                            benchmarkRuns.incrementAndGet();
                            observedConfig.set(config);
                            assertThat(previousState).isEmpty();
                            return completeOutcome(config, previousState, 10_000_000L);
                        });

        assertThat(selected).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(benchmarkRuns).hasValue(1);
        assertFixedProfile(observedConfig.get(), currentKey);
        assertThat(new GraphicsQualityStateStore(dataDirectory).load())
                .contains(
                        new GraphicsQualityState(
                                currentKey, GraphicsQualityPreset.MEDIUM, Optional.empty()));
        assertThat(new GraphicsBenchmarkReportStore(dataDirectory).load()).isPresent();
    }

    @Test
    void staleStateRunsOnceAndPreservesManualOverride()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path currentAssetLock =
                writeAssetLock("current-stale.lock", "{\"packs\":[2],\"schema\":1}");
        Path staleAssetLock = writeAssetLock("previous-stale.lock", "{\"packs\":[3],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(currentAssetLock).compatibilityKey();
        GraphicsBenchmarkCompatibilityKey staleKey =
                GraphicsBenchmarkAssetIdentity.fromLock(staleAssetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("stale-data");
        GraphicsQualityState staleState =
                new GraphicsQualityState(
                        staleKey,
                        GraphicsQualityPreset.LOW,
                        Optional.of(GraphicsQualityPreset.HIGH));
        new GraphicsQualityStateStore(dataDirectory).save(staleState);
        AtomicInteger benchmarkRuns = new AtomicInteger();

        GraphicsQualityPreset selected =
                GraphicsAutomaticQualitySelection.resolve(
                        dataDirectory,
                        currentAssetLock,
                        Optional.of(REPOSITORY_COMMIT),
                        (config, previousState) -> {
                            benchmarkRuns.incrementAndGet();
                            assertThat(previousState).contains(staleState);
                            return completeOutcome(config, previousState, 10_000_000L);
                        });

        assertThat(selected).isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(benchmarkRuns).hasValue(1);
        assertThat(new GraphicsQualityStateStore(dataDirectory).load())
                .contains(
                        new GraphicsQualityState(
                                currentKey,
                                GraphicsQualityPreset.MEDIUM,
                                Optional.of(GraphicsQualityPreset.HIGH)));
    }

    @Test
    void missingProvenanceDoesNotRunOrPersistBenchmark() throws IOException {
        Path assetLock = writeAssetLock("no-provenance.lock", "{\"packs\":[4],\"schema\":1}");
        Path dataDirectory = tempDirectory.resolve("no-provenance-data");
        AtomicInteger benchmarkRuns = new AtomicInteger();

        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsAutomaticQualitySelection.resolve(
                                        dataDirectory,
                                        assetLock,
                                        Optional.empty(),
                                        (config, previousState) -> {
                                            benchmarkRuns.incrementAndGet();
                                            throw new AssertionError("benchmark must not start");
                                        }));

        assertThat(benchmarkRuns).hasValue(0);
        assertThat(new GraphicsQualityStateStore(dataDirectory).load()).isEmpty();
        assertThat(new GraphicsBenchmarkReportStore(dataDirectory).load()).isEmpty();
    }

    @Test
    void mismatchedOutcomeDoesNotReplaceExistingStaleState() throws IOException {
        Path currentAssetLock =
                writeAssetLock("mismatch-current.lock", "{\"packs\":[5],\"schema\":1}");
        Path staleAssetLock = writeAssetLock("mismatch-stale.lock", "{\"packs\":[6],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey staleKey =
                GraphicsBenchmarkAssetIdentity.fromLock(staleAssetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("mismatch-data");
        GraphicsQualityState staleState =
                new GraphicsQualityState(staleKey, GraphicsQualityPreset.LOW, Optional.empty());
        new GraphicsQualityStateStore(dataDirectory).save(staleState);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsAutomaticQualitySelection.resolve(
                                        dataDirectory,
                                        currentAssetLock,
                                        Optional.of(REPOSITORY_COMMIT),
                                        (config, previousState) -> {
                                            GraphicsBenchmarkSession.Config mismatched =
                                                    GraphicsStartupBenchmarkProfile.config(
                                                            REPOSITORY_COMMIT, staleKey);
                                            return completeOutcome(
                                                    mismatched, previousState, 10_000_000L);
                                        }));

        assertThat(new GraphicsQualityStateStore(dataDirectory).load()).contains(staleState);
        assertThat(new GraphicsBenchmarkReportStore(dataDirectory).load()).isEmpty();
    }

    private Path writeAssetLock(String name, String content) throws IOException {
        Path assetLock = tempDirectory.resolve(name);
        assertThat(Files.writeString(assetLock, content, StandardCharsets.UTF_8))
                .isEqualTo(assetLock);
        return assetLock;
    }

    private static GraphicsBenchmarkSession.Outcome completeOutcome(
            GraphicsBenchmarkSession.Config config,
            Optional<GraphicsQualityState> previousState,
            long frameTimeNanos) {
        GraphicsBenchmarkSession session = new GraphicsBenchmarkSession(config, previousState);
        GraphicsTelemetrySample sample =
                new GraphicsTelemetrySample(frameTimeNanos, OptionalLong.empty(), 1L, 1, 1);
        int totalFrames = config.warmUpFrameCount() + config.measurementFrameCount();
        for (int index = 0; index < totalFrames; index++) {
            session.accept(sample);
        }
        return session.outcome().orElseThrow();
    }

    private static void assertFixedProfile(
            GraphicsBenchmarkSession.Config config, GraphicsBenchmarkCompatibilityKey expectedKey) {
        assertThat(config.repositoryCommit()).isEqualTo(REPOSITORY_COMMIT);
        assertThat(config.compatibilityKey()).isEqualTo(expectedKey);
        assertThat(config.measuredPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(config.width()).isEqualTo(1280);
        assertThat(config.height()).isEqualTo(720);
        assertThat(config.renderScale()).isEqualTo(1.0d);
        assertThat(config.warmUpFrameCount()).isEqualTo(120);
        assertThat(config.measurementFrameCount()).isEqualTo(240);
    }
}
