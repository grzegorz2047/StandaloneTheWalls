package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;
import java.util.Optional;

/** Decides whether startup can reuse a persisted quality choice or must benchmark again. */
public record GraphicsQualityStartupDecision(
        Action action, Optional<GraphicsQualityPreset> preset) {
    public GraphicsQualityStartupDecision {
        Objects.requireNonNull(action, "action");
        preset = Objects.requireNonNull(preset, "preset");
        if ((action == Action.RUN_BENCHMARK) == preset.isPresent()) {
            throw new IllegalArgumentException(
                    "startup decision action and preset are inconsistent");
        }
    }

    public static GraphicsQualityStartupDecision evaluate(
            Optional<GraphicsQualityState> persistedState,
            GraphicsBenchmarkCompatibilityKey currentKey) {
        Objects.requireNonNull(persistedState, "persistedState");
        Objects.requireNonNull(currentKey, "currentKey");
        if (persistedState.isEmpty()
                || persistedState.orElseThrow().requiresBenchmark(currentKey)) {
            return new GraphicsQualityStartupDecision(Action.RUN_BENCHMARK, Optional.empty());
        }
        return new GraphicsQualityStartupDecision(
                Action.USE_PERSISTED_PRESET,
                Optional.of(persistedState.orElseThrow().effectivePreset()));
    }

    public enum Action {
        RUN_BENCHMARK,
        USE_PERSISTED_PRESET
    }
}
