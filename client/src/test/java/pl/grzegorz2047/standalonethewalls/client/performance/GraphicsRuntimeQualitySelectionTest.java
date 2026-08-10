package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsRuntimeQualitySelectionTest {
    @TempDir Path tempDirectory;

    @Test
    void returnsCompatibleEffectivePresetIncludingManualOverride() throws IOException {
        Path assetLock = assetLock("current.lock", "{\"packs\":[],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey key =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path recommendedDirectory = tempDirectory.resolve("recommended");
        Path overrideDirectory = tempDirectory.resolve("override");
        new GraphicsQualityStateStore(recommendedDirectory)
                .save(new GraphicsQualityState(key, GraphicsQualityPreset.LOW, Optional.empty()));
        new GraphicsQualityStateStore(overrideDirectory)
                .save(
                        new GraphicsQualityState(
                                key,
                                GraphicsQualityPreset.LOW,
                                Optional.of(GraphicsQualityPreset.HIGH)));

        assertThat(
                        GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                recommendedDirectory, assetLock))
                .contains(GraphicsQualityPreset.LOW);
        assertThat(
                        GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                overrideDirectory, assetLock))
                .contains(GraphicsQualityPreset.HIGH);
    }

    @Test
    void missingOrStaleStateDoesNotSelectARuntimePreset() throws IOException {
        Path currentLock = assetLock("current.lock", "{\"packs\":[],\"schema\":1}");
        Path staleLock = assetLock("stale.lock", "{\"packs\":[1],\"schema\":1}");
        GraphicsBenchmarkCompatibilityKey staleKey =
                GraphicsBenchmarkAssetIdentity.fromLock(staleLock).compatibilityKey();
        Path staleDirectory = tempDirectory.resolve("stale");
        new GraphicsQualityStateStore(staleDirectory)
                .save(
                        new GraphicsQualityState(
                                staleKey, GraphicsQualityPreset.LOW, Optional.empty()));

        assertThat(
                        GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                tempDirectory.resolve("missing"), currentLock))
                .isEmpty();
        assertThat(
                        GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                staleDirectory, currentLock))
                .isEmpty();
    }

    @Test
    void malformedStateAndMissingAssetLockRemainExplicitIoFailures() throws IOException {
        Path assetLock = assetLock("current.lock", "{\"packs\":[],\"schema\":1}");
        Path malformedDirectory = tempDirectory.resolve("malformed");
        assertThat(Files.createDirectories(malformedDirectory)).isEqualTo(malformedDirectory);
        Path stateFile = malformedDirectory.resolve(GraphicsQualityStateStore.FILE_NAME);
        assertThat(Files.writeString(stateFile, "not-a-state\n", StandardCharsets.UTF_8))
                .isEqualTo(stateFile);

        assertThatIOException()
                .isThrownBy(
                        () ->
                                GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                        malformedDirectory, assetLock));
        assertThatIOException()
                .isThrownBy(
                        () ->
                                GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                                        tempDirectory.resolve("data"),
                                        tempDirectory.resolve("missing.lock")));
    }

    private Path assetLock(String name, String content) throws IOException {
        Path lock = tempDirectory.resolve(name);
        assertThat(Files.writeString(lock, content, StandardCharsets.UTF_8)).isEqualTo(lock);
        return lock;
    }
}
