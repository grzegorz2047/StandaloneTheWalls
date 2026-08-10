package pl.grzegorz2047.standalonethewalls.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsBenchmarkAssetIdentity;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsBenchmarkCompatibilityKey;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsQualityPreset;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsQualityState;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsQualityStateStore;

class ClientLauncherQualitySelectionTest {
    @TempDir Path tempDirectory;

    @Test
    void compatiblePersistedPresetIsReusableByInteractiveLauncher() throws IOException {
        Path assetLock = writeAssetLock();
        GraphicsBenchmarkCompatibilityKey key =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Path dataDirectory = tempDirectory.resolve("data");
        new GraphicsQualityStateStore(dataDirectory)
                .save(new GraphicsQualityState(key, GraphicsQualityPreset.LOW, Optional.empty()));
        ClientLaunchOptions options = options(dataDirectory);

        assertThat(ClientLauncher.resolveRuntimePreset(options, Optional.of(assetLock)))
                .contains(GraphicsQualityPreset.LOW);
    }

    @Test
    void missingAssetLockAndMalformedStateFallBackToNativeRendering() throws IOException {
        Path dataDirectory = tempDirectory.resolve("malformed-data");
        assertThat(Files.createDirectories(dataDirectory)).isEqualTo(dataDirectory);
        Path stateFile = dataDirectory.resolve(GraphicsQualityStateStore.FILE_NAME);
        assertThat(Files.writeString(stateFile, "not-a-state\n", StandardCharsets.UTF_8))
                .isEqualTo(stateFile);
        ClientLaunchOptions options = options(dataDirectory);
        Path assetLock = writeAssetLock();

        assertThat(ClientLauncher.resolveRuntimePreset(options, Optional.empty())).isEmpty();
        assertThat(ClientLauncher.resolveRuntimePreset(options, Optional.of(assetLock))).isEmpty();
        assertThat(
                        ClientLauncher.resolveRuntimePreset(
                                options, Optional.of(tempDirectory.resolve("missing.lock"))))
                .isEmpty();
    }

    private Path writeAssetLock() throws IOException {
        Path lock = tempDirectory.resolve("assets.lock.json");
        assertThat(Files.writeString(
                        lock, "{\"packs\":[],\"schema\":1}", StandardCharsets.UTF_8))
                .isEqualTo(lock);
        return lock;
    }

    private static ClientLaunchOptions options(Path dataDirectory) {
        return new ClientLaunchOptions(ClientLanguage.ENGLISH, false, false, dataDirectory);
    }
}
