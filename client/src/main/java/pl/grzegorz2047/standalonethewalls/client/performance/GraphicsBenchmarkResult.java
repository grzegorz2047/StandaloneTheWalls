package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/** Report-ready local benchmark result without any automatic telemetry transport. */
public record GraphicsBenchmarkResult(
        GraphicsQualityPreset recommendedPreset,
        TargetStatus targetStatus,
        FrameTimeStatistics statistics,
        int width,
        int height,
        double renderScale) {
    private static final int MAXIMUM_DIMENSION = 16_384;

    public GraphicsBenchmarkResult {
        Objects.requireNonNull(recommendedPreset, "recommendedPreset");
        Objects.requireNonNull(targetStatus, "targetStatus");
        Objects.requireNonNull(statistics, "statistics");
        if (width < 1 || width > MAXIMUM_DIMENSION || height < 1 || height > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("benchmark resolution is outside the bounded range");
        }
        if (!Double.isFinite(renderScale) || renderScale < 0.50d || renderScale > 1.00d) {
            throw new IllegalArgumentException("renderScale is outside the bounded range");
        }
    }

    public boolean requiresRenderScaleReduction() {
        return targetStatus == TargetStatus.BELOW_MINIMUM_TARGET;
    }

    public enum TargetStatus {
        MEETS_PRIMARY_TARGET,
        MEETS_MINIMUM_TARGET,
        BELOW_MINIMUM_TARGET
    }
}
