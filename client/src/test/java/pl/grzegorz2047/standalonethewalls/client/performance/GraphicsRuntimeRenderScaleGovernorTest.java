package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GraphicsRuntimeRenderScaleGovernorTest {
    private static final long PRIMARY_BAD = 20_000_000L;
    private static final long PRIMARY_GOOD = 10_000_000L;
    private static final long LOW_BAD = 40_000_000L;

    @Test
    void productionBudgetsMatchExistingPrimaryAndMinimumTargets() {
        assertThat(
                        new GraphicsRuntimeRenderScaleGovernor(GraphicsQualityPreset.LOW)
                                .p95BudgetNanos())
                .isEqualTo(BenchmarkQualitySelector.MINIMUM_TARGET_P95_NANOS);
        assertThat(
                        new GraphicsRuntimeRenderScaleGovernor(GraphicsQualityPreset.MEDIUM)
                                .p95BudgetNanos())
                .isEqualTo(BenchmarkQualitySelector.PRIMARY_TARGET_P95_NANOS);
        assertThat(
                        new GraphicsRuntimeRenderScaleGovernor(GraphicsQualityPreset.HIGH)
                                .p95BudgetNanos())
                .isEqualTo(BenchmarkQualitySelector.PRIMARY_TARGET_P95_NANOS);
    }

    @Test
    void partialAndNMinusOneBadWindowsDoNotReduceScale() {
        GraphicsRuntimeRenderScaleGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 2, 0.05d, 2);

        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).isEmpty();
        assertThat(governor.pendingSampleCount()).isOne();
        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).isEmpty();
        assertThat(governor.pendingSampleCount()).isZero();
        assertThat(governor.consecutiveOverBudgetWindows()).isOne();
        assertThat(governor.currentRenderScale()).isEqualTo(1.0d);

        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).isEmpty();
        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).hasValue(0.95d);
        assertThat(governor.pendingSampleCount()).isZero();
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void healthyCompleteWindowResetsBadWindowStreakWithoutUpgrading() {
        GraphicsRuntimeRenderScaleGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 1, 0.05d, 2);

        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).isEmpty();
        assertThat(governor.consecutiveOverBudgetWindows()).isOne();
        assertThat(governor.acceptFrameTime(PRIMARY_GOOD)).isEmpty();
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).isEmpty();
        assertThat(governor.acceptFrameTime(PRIMARY_BAD)).hasValue(0.95d);
        assertThat(governor.acceptFrameTime(PRIMARY_GOOD)).isEmpty();
        assertThat(governor.currentRenderScale()).isEqualTo(0.95d);
    }

    @Test
    void eachPresetStopsAtItsExistingMinimum() {
        assertMinimum(GraphicsQualityPreset.LOW, LOW_BAD, 0.67d);
        assertMinimum(GraphicsQualityPreset.MEDIUM, PRIMARY_BAD, 0.75d);
        assertMinimum(GraphicsQualityPreset.HIGH, PRIMARY_BAD, 0.85d);
    }

    @Test
    void rejectsInvalidWindowsAndFrameTimes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> governor(GraphicsQualityPreset.MEDIUM, 0, 0.05d, 2));
        GraphicsRuntimeRenderScaleGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 1, 0.05d, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> governor.acceptFrameTime(0L));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                governor.acceptFrameTime(
                                        FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L));
    }

    private static void assertMinimum(
            GraphicsQualityPreset preset, long badFrameTime, double expectedMinimum) {
        GraphicsRuntimeRenderScaleGovernor governor = governor(preset, 1, 0.05d, 1);
        for (int index = 0; index < 20; index++) {
            governor.acceptFrameTime(badFrameTime);
        }
        assertThat(governor.currentRenderScale()).isEqualTo(expectedMinimum);
        assertThat(governor.acceptFrameTime(badFrameTime)).isEmpty();
        assertThat(governor.currentRenderScale()).isEqualTo(expectedMinimum);
    }

    private static GraphicsRuntimeRenderScaleGovernor governor(
            GraphicsQualityPreset preset,
            int windowSampleCount,
            double reductionStep,
            int requiredWindows) {
        return new GraphicsRuntimeRenderScaleGovernor(
                preset, windowSampleCount, reductionStep, requiredWindows);
    }
}
