package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphicsQualityStartupDecisionTest {
    private static final GraphicsBenchmarkCompatibilityKey CURRENT_KEY =
            new GraphicsBenchmarkCompatibilityKey("core", "7", "first-run", 2);

    @Test
    void missingOrIncompatibleStateRequiresBenchmark() {
        GraphicsQualityStartupDecision missing =
                GraphicsQualityStartupDecision.evaluate(Optional.empty(), CURRENT_KEY);
        GraphicsQualityState staleState =
                new GraphicsQualityState(
                        new GraphicsBenchmarkCompatibilityKey("core", "6", "first-run", 2),
                        GraphicsQualityPreset.MEDIUM,
                        Optional.of(GraphicsQualityPreset.LOW));
        GraphicsQualityStartupDecision stale =
                GraphicsQualityStartupDecision.evaluate(Optional.of(staleState), CURRENT_KEY);

        assertThat(missing.action()).isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        assertThat(missing.preset()).isEmpty();
        assertThat(stale.action()).isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        assertThat(stale.preset()).isEmpty();
    }

    @Test
    void compatibleStateUsesRecommendationUnlessManualOverrideExists() {
        GraphicsQualityState automatic =
                new GraphicsQualityState(
                        CURRENT_KEY, GraphicsQualityPreset.MEDIUM, Optional.empty());
        GraphicsQualityState overridden =
                automatic.withManualOverride(Optional.of(GraphicsQualityPreset.LOW));

        assertThat(
                        GraphicsQualityStartupDecision.evaluate(Optional.of(automatic), CURRENT_KEY)
                                .preset())
                .contains(GraphicsQualityPreset.MEDIUM);
        assertThat(
                        GraphicsQualityStartupDecision.evaluate(
                                        Optional.of(overridden), CURRENT_KEY)
                                .preset())
                .contains(GraphicsQualityPreset.LOW);
        assertThat(overridden.effectivePreset()).isEqualTo(GraphicsQualityPreset.LOW);
    }

    @Test
    void refreshingRecommendationPreservesManualOverride() {
        GraphicsQualityState previous =
                new GraphicsQualityState(
                        new GraphicsBenchmarkCompatibilityKey("core", "6", "first-run", 1),
                        GraphicsQualityPreset.LOW,
                        Optional.of(GraphicsQualityPreset.HIGH));

        GraphicsQualityState refreshed =
                previous.refreshRecommendation(CURRENT_KEY, GraphicsQualityPreset.MEDIUM);

        assertThat(refreshed.compatibilityKey()).isEqualTo(CURRENT_KEY);
        assertThat(refreshed.recommendedPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(refreshed.manualOverride()).contains(GraphicsQualityPreset.HIGH);
        assertThat(refreshed.effectivePreset()).isEqualTo(GraphicsQualityPreset.HIGH);
        assertThat(refreshed.requiresBenchmark(CURRENT_KEY)).isFalse();
    }

    @Test
    void decisionRejectsInconsistentForgedState() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityStartupDecision(
                                        GraphicsQualityStartupDecision.Action.RUN_BENCHMARK,
                                        Optional.of(GraphicsQualityPreset.LOW)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsQualityStartupDecision(
                                        GraphicsQualityStartupDecision.Action.USE_PERSISTED_PRESET,
                                        Optional.empty()));
    }
}
