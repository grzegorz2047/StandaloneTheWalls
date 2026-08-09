package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class DynamicRenderScaleGovernorTest {
    private static final long BUDGET_NANOS = 16_700_000L;
    private static final long OVER_BUDGET_NANOS = 20_000_000L;

    @Test
    void startsAtEachPresetDefaultWithinItsBounds() {
        DynamicRenderScaleGovernor low = governor(GraphicsQualityPreset.LOW, 0.05d, 2);
        DynamicRenderScaleGovernor medium = governor(GraphicsQualityPreset.MEDIUM, 0.05d, 2);
        DynamicRenderScaleGovernor high = governor(GraphicsQualityPreset.HIGH, 0.05d, 2);

        assertThat(low.currentRenderScale()).isEqualTo(0.75d);
        assertThat(medium.currentRenderScale()).isEqualTo(1.00d);
        assertThat(high.currentRenderScale()).isEqualTo(1.00d);
        assertThat(low.atMinimumScale()).isFalse();
        assertThat(medium.atMinimumScale()).isFalse();
        assertThat(high.atMinimumScale()).isFalse();
    }

    @Test
    void reducesExactlyOneStepOnRequiredConsecutiveBadWindow() {
        DynamicRenderScaleGovernor governor = governor(GraphicsQualityPreset.MEDIUM, 0.10d, 3);

        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(1.00d);
        assertThat(governor.consecutiveOverBudgetWindows()).isOne();
        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(1.00d);
        assertThat(governor.consecutiveOverBudgetWindows()).isEqualTo(2);
        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(0.90d);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void healthyWindowResetsTheBadWindowStreakWithoutIncreasingScale() {
        DynamicRenderScaleGovernor governor = governor(GraphicsQualityPreset.HIGH, 0.10d, 2);

        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(1.00d);
        assertThat(governor.consecutiveOverBudgetWindows()).isOne();
        assertThat(governor.observe(BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(1.00d);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();

        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(1.00d);
        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(0.90d);
        assertThat(governor.observe(10_000_000L, BUDGET_NANOS)).isEqualTo(0.90d);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void clampsExactlyAtEachPresetMinimumAndNeverBuildsLatentStreakThere() {
        assertMinimumClamp(GraphicsQualityPreset.LOW, 0.05d, 0.67d);
        assertMinimumClamp(GraphicsQualityPreset.MEDIUM, 0.10d, 0.75d);
        assertMinimumClamp(GraphicsQualityPreset.HIGH, 0.10d, 0.85d);
    }

    @Test
    void rejectsUnboundedConfigurationAndFrameTimes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DynamicRenderScaleGovernor(null, 0.05d, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new DynamicRenderScaleGovernor(GraphicsQualityPreset.LOW, 0.0d, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new DynamicRenderScaleGovernor(GraphicsQualityPreset.LOW, -0.05d, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new DynamicRenderScaleGovernor(
                                        GraphicsQualityPreset.LOW, Double.NaN, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new DynamicRenderScaleGovernor(
                                        GraphicsQualityPreset.LOW, Double.POSITIVE_INFINITY, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new DynamicRenderScaleGovernor(GraphicsQualityPreset.LOW, 1.01d, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new DynamicRenderScaleGovernor(GraphicsQualityPreset.LOW, 0.05d, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new DynamicRenderScaleGovernor(
                                        GraphicsQualityPreset.LOW, 0.05d, 121));

        DynamicRenderScaleGovernor governor = governor(GraphicsQualityPreset.LOW, 0.05d, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> governor.observe(0L, BUDGET_NANOS));
        assertThatIllegalArgumentException().isThrownBy(() -> governor.observe(1L, 0L));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                governor.observe(
                                        FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L,
                                        BUDGET_NANOS));
    }

    private static DynamicRenderScaleGovernor governor(
            GraphicsQualityPreset preset, double reductionStep, int requiredWindows) {
        return new DynamicRenderScaleGovernor(preset, reductionStep, requiredWindows);
    }

    private static void assertMinimumClamp(
            GraphicsQualityPreset preset, double reductionStep, double expectedMinimum) {
        DynamicRenderScaleGovernor governor = governor(preset, reductionStep, 2);

        for (int index = 0; index < 20 && !governor.atMinimumScale(); index++) {
            assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS))
                    .isGreaterThanOrEqualTo(expectedMinimum);
            assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS))
                    .isGreaterThanOrEqualTo(expectedMinimum);
        }

        assertThat(governor.atMinimumScale()).isTrue();
        assertThat(governor.currentRenderScale()).isEqualTo(expectedMinimum);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
        assertThat(governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS)).isEqualTo(expectedMinimum);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }
}
