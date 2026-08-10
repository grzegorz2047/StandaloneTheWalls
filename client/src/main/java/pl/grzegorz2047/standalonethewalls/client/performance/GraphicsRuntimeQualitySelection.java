package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Resolves a persisted effective preset only when it matches current benchmark compatibility. */
public final class GraphicsRuntimeQualitySelection {
    private GraphicsRuntimeQualitySelection() {
        throw new AssertionError("No instances");
    }

    public static Optional<GraphicsQualityPreset> compatiblePersistedPreset(
            Path dataDirectory, Path assetLock) throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(assetLock, "assetLock");
        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        Optional<GraphicsQualityState> persistedState =
                new GraphicsQualityStateStore(dataDirectory).load();
        return GraphicsQualityStartupDecision.evaluate(persistedState, currentKey).preset();
    }
}
