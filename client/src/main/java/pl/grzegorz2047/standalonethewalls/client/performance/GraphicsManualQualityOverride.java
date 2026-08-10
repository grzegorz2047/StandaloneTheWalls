package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Updates the user-selected preset only when a compatible persisted recommendation exists. */
public final class GraphicsManualQualityOverride {
    private GraphicsManualQualityOverride() {
        throw new AssertionError("No instances");
    }

    public static GraphicsQualityPreset apply(
            Path dataDirectory, Path assetLock, Optional<GraphicsQualityPreset> manualOverride)
            throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(assetLock, "assetLock");
        Objects.requireNonNull(manualOverride, "manualOverride");

        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        GraphicsQualityStateStore store = new GraphicsQualityStateStore(dataDirectory);
        GraphicsQualityState currentState =
                store.load()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "graphics preset preference requires existing quality state"));
        if (currentState.requiresBenchmark(currentKey)) {
            throw new IllegalStateException(
                    "graphics preset preference requires state compatible with current assets");
        }

        GraphicsQualityState updated = currentState.withManualOverride(manualOverride);
        store.save(updated);
        return updated.effectivePreset();
    }
}
