package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class QualityDowngradeGovernorTest {
    private static final long BUDGET_NANOS = 16_700_000L;
    private static final long OVER_BUDGET_NANOS = 20_000_000L;

    @Test
    void ignoresSingleSpikeAndResetsOnHealthyWindow() {
        QualityDowngradeGovernor governor = new QualityDowngradeGovernor(3);

        assertThat(governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(governor.consecutiveOverBudgetWindows()).isEqualTo(1);

        assertThat(governor.observe(GraphicsQualityPreset.HIGH, BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void downgradesExactlyOneLevelOnRequiredConsecutiveWindow() {
        QualityDowngradeGovernor governor = new QualityDowngradeGovernor(3);

        assertThat(governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void canProgressFromHighToMediumToLowButNeverBelowLow() {
        QualityDowngradeGovernor governor = new QualityDowngradeGovernor(2);

        assertThat(governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.HIGH);
        GraphicsQualityPreset medium =
                governor.observe(GraphicsQualityPreset.HIGH, OVER_BUDGET_NANOS, BUDGET_NANOS);
        assertThat(governor.observe(medium, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.MEDIUM);
        GraphicsQualityPreset low = governor.observe(medium, OVER_BUDGET_NANOS, BUDGET_NANOS);

        assertThat(medium).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(low).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(governor.observe(low, OVER_BUDGET_NANOS, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(governor.consecutiveOverBudgetWindows()).isZero();
    }

    @Test
    void neverAutomaticallyUpgradesQuality() {
        QualityDowngradeGovernor governor = new QualityDowngradeGovernor(2);

        assertThat(governor.observe(GraphicsQualityPreset.LOW, 10_000_000L, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(governor.observe(GraphicsQualityPreset.MEDIUM, 10_000_000L, BUDGET_NANOS))
                .isEqualTo(GraphicsQualityPreset.MEDIUM);
    }

    @Test
    void rejectsUnboundedConfigurationAndFrameTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new QualityDowngradeGovernor(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new QualityDowngradeGovernor(121));

        QualityDowngradeGovernor governor = new QualityDowngradeGovernor(2);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> governor.observe(GraphicsQualityPreset.HIGH, 0L, BUDGET_NANOS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> governor.observe(GraphicsQualityPreset.HIGH, 1L, 0L));
    }
}
