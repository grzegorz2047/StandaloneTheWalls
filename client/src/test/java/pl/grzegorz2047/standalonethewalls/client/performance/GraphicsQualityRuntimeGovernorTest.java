package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class GraphicsQualityRuntimeGovernorTest {
    private static final long BUDGET_NANOS = 16_700_000L;
    private static final long OVER_BUDGET_NANOS = 20_000_000L;

    @Test
    void exhaustsMediumRenderScaleBeforeCountingPresetDowngradeWindows() {
        GraphicsQualityRuntimeGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 0.10d, 2, 2);

        assertSnapshot(governor.snapshot(), GraphicsQualityPreset.MEDIUM, 1.00d, false, 0, 0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                1.00d,
                false,
                1,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.90d,
                false,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.90d,
                false,
                1,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.80d,
                false,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.80d,
                false,
                1,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                1);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.LOW,
                0.75d,
                false,
                0,
                0);
    }

    @Test
    void highDowngradesExactlyOnceOnlyAfterAlreadyReachingItsMinimumScale() {
        GraphicsQualityRuntimeGovernor governor = governor(GraphicsQualityPreset.HIGH, 0.10d, 1, 2);

        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                0.90d,
                false,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                0.85d,
                true,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                0.85d,
                true,
                0,
                1);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                1.00d,
                false,
                0,
                0);
    }

    @Test
    void healthyWindowResetsTheActiveScaleStreakWithoutUpgrading() {
        GraphicsQualityRuntimeGovernor governor = governor(GraphicsQualityPreset.HIGH, 0.10d, 2, 2);

        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                1.00d,
                false,
                1,
                0);
        assertSnapshot(
                governor.observe(BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                1.00d,
                false,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.HIGH,
                1.00d,
                false,
                1,
                0);
    }

    @Test
    void healthyWindowAtMinimumResetsPresetDowngradeStreak() {
        GraphicsQualityRuntimeGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 0.25d, 1, 2);

        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                1);
        assertSnapshot(
                governor.observe(10_000_000L, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                0);
        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.MEDIUM,
                0.75d,
                true,
                0,
                1);
    }

    @Test
    void lowMinimumIsTheFloorWithoutLatentPresetDowngradeState() {
        GraphicsQualityRuntimeGovernor governor = governor(GraphicsQualityPreset.LOW, 0.10d, 1, 2);

        assertSnapshot(
                governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                GraphicsQualityPreset.LOW,
                0.67d,
                true,
                0,
                0);
        for (int index = 0; index < 10; index++) {
            assertSnapshot(
                    governor.observe(OVER_BUDGET_NANOS, BUDGET_NANOS),
                    GraphicsQualityPreset.LOW,
                    0.67d,
                    true,
                    0,
                    0);
        }
    }

    @Test
    void rejectsInvalidConfigurationAndFrameTimes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GraphicsQualityRuntimeGovernor(null, 0.10d, 2, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityRuntimeGovernor(
                                        GraphicsQualityPreset.MEDIUM, 0.0d, 2, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityRuntimeGovernor(
                                        GraphicsQualityPreset.MEDIUM, Double.NaN, 2, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityRuntimeGovernor(
                                        GraphicsQualityPreset.MEDIUM, 0.10d, 0, 2));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityRuntimeGovernor(
                                        GraphicsQualityPreset.MEDIUM, 0.10d, 2, 0));

        GraphicsQualityRuntimeGovernor governor =
                governor(GraphicsQualityPreset.MEDIUM, 0.10d, 2, 2);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> governor.observe(0L, BUDGET_NANOS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> governor.observe(1L, 0L));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                governor.observe(
                                        FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L,
                                        BUDGET_NANOS));
    }

    private static GraphicsQualityRuntimeGovernor governor(
            GraphicsQualityPreset preset,
            double reductionStep,
            int requiredScaleWindows,
            int requiredPresetWindows) {
        return new GraphicsQualityRuntimeGovernor(
                preset, reductionStep, requiredScaleWindows, requiredPresetWindows);
    }

    private static void assertSnapshot(
            GraphicsQualityRuntimeGovernor.Snapshot snapshot,
            GraphicsQualityPreset preset,
            double renderScale,
            boolean minimumRenderScale,
            int renderScaleOverBudgetWindows,
            int presetOverBudgetWindows) {
        assertThat(snapshot.preset()).isEqualTo(preset);
        assertThat(snapshot.renderScale()).isEqualTo(renderScale);
        assertThat(snapshot.minimumRenderScale()).isEqualTo(minimumRenderScale);
        assertThat(snapshot.renderScaleOverBudgetWindows())
                .isEqualTo(renderScaleOverBudgetWindows);
        assertThat(snapshot.presetOverBudgetWindows()).isEqualTo(presetOverBudgetWindows);
    }
}
