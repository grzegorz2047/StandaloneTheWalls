package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GraphicsBenchmarkResultTest {
    private static final FrameTimeStatistics STATISTICS =
            new FrameTimeStatistics(1, 16_000_000L, 16_000_000L, 16_000_000L);

    @Test
    void preservesReportReadyMeasurementContext() {
        GraphicsBenchmarkResult result =
                new GraphicsBenchmarkResult(
                        GraphicsQualityPreset.MEDIUM,
                        GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET,
                        STATISTICS,
                        1920,
                        1080,
                        1.0d);

        assertThat(result.recommendedPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(result.statistics()).isEqualTo(STATISTICS);
        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.height()).isEqualTo(1080);
        assertThat(result.renderScale()).isEqualTo(1.0d);
        assertThat(result.requiresRenderScaleReduction()).isFalse();
    }

    @Test
    void rejectsInvalidResolutionAndRenderScale() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkResult(
                                        GraphicsQualityPreset.LOW,
                                        GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET,
                                        STATISTICS,
                                        0,
                                        720,
                                        1.0d));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkResult(
                                        GraphicsQualityPreset.LOW,
                                        GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET,
                                        STATISTICS,
                                        1280,
                                        720,
                                        0.49d));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkResult(
                                        GraphicsQualityPreset.LOW,
                                        GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET,
                                        STATISTICS,
                                        1280,
                                        720,
                                        Double.NaN));
    }
}
