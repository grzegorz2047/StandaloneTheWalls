package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsManualQualityOverrideTest {
    private static final String REPOSITORY_COMMIT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir Path tempDirectory;

    @Test
    void explicitPresetPreservesRecommendationAndCompatibilityKey() throws IOException {
        Path assetLock = writeAssetLock("current.lock", "{\"packs\":[1],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("explicit-data");
        GraphicsQualityState initial =
                new GraphicsQualityState(
                        currentKey, GraphicsQualityPreset.MEDIUM, Optional.empty());
        new GraphicsQualityStateStore(dataDirectory).save(initial);

        GraphicsQualityPreset effective =
                GraphicsManualQualityOverride.apply(
                        dataDirectory, assetLock, Optional.of(GraphicsQualityPreset.HIGH));

        assertThat(effective).isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(new GraphicsQualityStateStore(dataDirectory).load())
                .contains(
                        new GraphicsQualityState(
                                currentKey,
                                GraphicsQualityPreset.MEDIUM,
                                Optional.of(GraphicsQualityPreset.HIGH)));
    }

    @Test
    void autoClearsOnlyOverrideAndReturnsStoredRecommendation() throws IOException {
        Path assetLock = writeAssetLock("auto.lock", "{\"packs\":[2],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("auto-data");
        new GraphicsQualityStateStore(dataDirectory)
                .save(
                        new GraphicsQualityState(
                                currentKey,
                                GraphicsQualityPreset.LOW,
                                Optional.of(GraphicsQualityPreset.HIGH)));

        GraphicsQualityPreset effective =
                GraphicsManualQualityOverride.apply(dataDirectory, assetLock, Optional.empty());

        assertThat(effective).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(new GraphicsQualityStateStore(dataDirectory).load())
                .contains(
                        new GraphicsQualityState(
                                currentKey, GraphicsQualityPreset.LOW, Optional.empty()));
    }

    @Test
    void missingOrIncompatibleStateIsRejectedWithoutFabrication() throws IOException {
        Path currentAssetLock =
                writeAssetLock("current-missing.lock", "{\"packs\":[3],\"schema\":1}");
        Path staleAssetLock =
                writeAssetLock("stale-missing.lock", "{\"packs\":[4],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey staleKey =
                GraphicsBenchmarkAssetIdentity.fromLock(staleAssetLock).compatibilityKey();
        Path missingData = tempDirectory.resolve("missing-data");
        Path staleData = tempDirectory.resolve("stale-data");
        GraphicsQualityState staleState =
                new GraphicsQualityState(staleKey, GraphicsQualityPreset.LOW, Optional.empty());
        new GraphicsQualityStateStore(staleData).save(staleState);

        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsManualQualityOverride.apply(
                                        missingData,
                                        currentAssetLock,
                                        Optional.of(GraphicsQualityPreset.HIGH)));
        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsManualQualityOverride.apply(
                                        staleData,
                                        currentAssetLock,
                                        Optional.of(GraphicsQualityPreset.HIGH)));

        assertThat(new GraphicsQualityStateStore(missingData).load()).isEmpty();
        assertThat(new GraphicsQualityStateStore(staleData).load()).contains(staleState);
    }

    @Test
    void compatibleStateSkipsAutomaticBenchmarkBeforeApplyingOverride()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path assetLock = writeAssetLock("skip.lock", "{\"packs\":[5],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("skip-data");
        new GraphicsQualityStateStore(dataDirectory)
                .save(
                        new GraphicsQualityState(
                                currentKey, GraphicsQualityPreset.MEDIUM, Optional.empty()));
        AtomicInteger benchmarkRuns = new AtomicInteger();

        GraphicsQualityPreset automatic =
                GraphicsAutomaticQualitySelection.resolve(
                        dataDirectory,
                        assetLock,
                        Optional.of(REPOSITORY_COMMIT),
                        (config, previousState) -> {
                            benchmarkRuns.incrementAndGet();
                            throw new AssertionError("compatible state must skip benchmark");
                        });
        GraphicsQualityPreset effective =
                GraphicsManualQualityOverride.apply(
                        dataDirectory, assetLock, Optional.of(GraphicsQualityPreset.LOW));

        assertThat(automatic).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(effective).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(benchmarkRuns).hasValue(0);
    }

    private Path writeAssetLock(String name, String content) throws IOException {
        Path assetLock = tempDirectory.resolve(name);
        Files.writeString(assetLock, content, StandardCharsets.UTF_8);
        return assetLock;
    }
}
