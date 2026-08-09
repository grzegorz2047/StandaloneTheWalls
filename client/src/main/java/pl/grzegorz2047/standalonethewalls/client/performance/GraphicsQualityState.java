package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;
import java.util.Optional;

/** Persisted benchmark recommendation plus an explicit user quality override. */
public record GraphicsQualityState(
        GraphicsBenchmarkCompatibilityKey compatibilityKey,
        GraphicsQualityPreset recommendedPreset,
        Optional<GraphicsQualityPreset> manualOverride) {
    public GraphicsQualityState {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        Objects.requireNonNull(recommendedPreset, "recommendedPreset");
        manualOverride = Objects.requireNonNull(manualOverride, "manualOverride");
    }

    public GraphicsQualityPreset effectivePreset() {
        return manualOverride.orElse(recommendedPreset);
    }

    public boolean requiresBenchmark(GraphicsBenchmarkCompatibilityKey currentKey) {
        return !compatibilityKey.equals(Objects.requireNonNull(currentKey, "currentKey"));
    }

    public GraphicsQualityState refreshRecommendation(
            GraphicsBenchmarkCompatibilityKey currentKey,
            GraphicsQualityPreset newRecommendedPreset) {
        return new GraphicsQualityState(
                Objects.requireNonNull(currentKey, "currentKey"),
                Objects.requireNonNull(newRecommendedPreset, "newRecommendedPreset"),
                manualOverride);
    }

    public GraphicsQualityState withManualOverride(Optional<GraphicsQualityPreset> newOverride) {
        return new GraphicsQualityState(
                compatibilityKey,
                recommendedPreset,
                Objects.requireNonNull(newOverride, "newOverride"));
    }
}
