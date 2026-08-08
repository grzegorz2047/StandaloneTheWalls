package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BenchmarkQualitySelectorTest {
    @Test
    void mapsTheExactPrimaryBoundaryToMedium() {
        GraphicsBenchmarkResult result =
                BenchmarkQualitySelector.select(
                        statistics(BenchmarkQualitySelector.PRIMARY_TARGET_P95_NANOS),
                        1920,
                        1080,
                        1.0d);

        assertThat(result.recommendedPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(result.targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET);
        assertThat(result.requiresRenderScaleReduction()).isFalse();
    }

    @Test
    void mapsJustAbovePrimaryAndExactMinimumBoundaryToLow() {
        GraphicsBenchmarkResult justAbovePrimary =
                BenchmarkQualitySelector.select(
                        statistics(BenchmarkQualitySelector.PRIMARY_TARGET_P95_NANOS + 1L),
                        1280,
                        720,
                        1.0d);
        GraphicsBenchmarkResult exactMinimum =
                BenchmarkQualitySelector.select(
                        statistics(BenchmarkQualitySelector.MINIMUM_TARGET_P95_NANOS),
                        1280,
                        720,
                        1.0d);

        assertThat(justAbovePrimary.recommendedPreset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(justAbovePrimary.targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET);
        assertThat(exactMinimum.recommendedPreset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(exactMinimum.targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET);
    }

    @Test
    void marksJustAboveMinimumAsBelowTargetWithoutInventingHighSelection() {
        GraphicsBenchmarkResult result =
                BenchmarkQualitySelector.select(
                        statistics(BenchmarkQualitySelector.MINIMUM_TARGET_P95_NANOS + 1L),
                        1280,
                        720,
                        0.75d);

        assertThat(result.recommendedPreset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(result.targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.BELOW_MINIMUM_TARGET);
        assertThat(result.requiresRenderScaleReduction()).isTrue();
        assertThat(result.recommendedPreset()).isNotEqualTo(GraphicsQualityPreset.HIGH);
    }

    private static FrameTimeStatistics statistics(long p95Nanos) {
        return new FrameTimeStatistics(1, p95Nanos, p95Nanos, p95Nanos);
    }
}
