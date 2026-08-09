package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsQualityStartupCoordinatorTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final GraphicsBenchmarkCompatibilityKey CURRENT_KEY =
            new GraphicsBenchmarkCompatibilityKey("core", "9", "reference", 3);
    private static final GraphicsBenchmarkCompatibilityKey STALE_KEY =
            new GraphicsBenchmarkCompatibilityKey("core", "8", "reference", 2);

    @TempDir Path tempDirectory;

    @Test
    void missingStateRequiresBenchmarkWithoutPreviousState() throws IOException {
        GraphicsQualityStartupCoordinator coordinator = coordinator();

        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();

        assertThat(plan.action())
                .isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        assertThat(plan.effectivePreset()).isEmpty();
        assertThat(plan.benchmarkPreviousState()).isEmpty();
    }

    @Test
    void compatibleStateUsesItsEffectiveManualOverrideWithoutBenchmark() throws IOException {
        GraphicsQualityState state =
                new GraphicsQualityState(
                        CURRENT_KEY, GraphicsQualityPreset.LOW, Optional.of(GraphicsQualityPreset.HIGH));
        new GraphicsQualityStateStore(tempDirectory).save(state);
        GraphicsQualityStartupCoordinator coordinator = coordinator();

        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();

        assertThat(plan.action())
                .isEqualTo(GraphicsQualityStartupDecision.Action.USE_PERSISTED_PRESET);
        assertThat(plan.effectivePreset()).contains(GraphicsQualityPreset.HIGH);
        assertThat(plan.benchmarkPreviousState()).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> coordinator.completeBenchmark(outcome(CURRENT_KEY, Optional.empty())));
    }

    @Test
    void staleStateIsRetainedOnlyAsBenchmarkContextAndOverrideSurvivesRefresh() throws IOException {
        GraphicsQualityState staleState =
                new GraphicsQualityState(
                        STALE_KEY,
                        GraphicsQualityPreset.LOW,
                        Optional.of(GraphicsQualityPreset.HIGH));
        GraphicsQualityStateStore store = new GraphicsQualityStateStore(tempDirectory);
        store.save(staleState);
        GraphicsQualityStartupCoordinator coordinator = coordinator();

        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();

        assertThat(plan.action())
                .isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        assertThat(plan.effectivePreset()).isEmpty();
        assertThat(plan.benchmarkPreviousState()).contains(staleState);

        GraphicsBenchmarkSession.Outcome outcome =
                outcome(CURRENT_KEY, plan.benchmarkPreviousState());
        GraphicsQualityPreset effectivePreset = coordinator.completeBenchmark(outcome);

        assertThat(effectivePreset).isEqualTo(GraphicsQualityPreset.HIGH);
        GraphicsQualityState persisted = store.load().orElseThrow();
        assertThat(persisted.compatibilityKey()).isEqualTo(CURRENT_KEY);
        assertThat(persisted.recommendedPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(persisted.manualOverride()).contains(GraphicsQualityPreset.HIGH);
    }

    @Test
    void mismatchedBenchmarkOutcomeIsRejectedWithoutWritingState() throws IOException {
        GraphicsQualityStartupCoordinator coordinator = coordinator();
        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();
        assertThat(plan.action())
                .isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        GraphicsBenchmarkSession.Outcome mismatched = outcome(STALE_KEY, Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> coordinator.completeBenchmark(mismatched));

        assertThat(new GraphicsQualityStateStore(tempDirectory).load()).isEmpty();
    }

    @Test
    void completionMustFollowOneBenchmarkRequiredPlanAndCannotRepeat() throws IOException {
        GraphicsQualityStartupCoordinator coordinator = coordinator();
        GraphicsBenchmarkSession.Outcome validOutcome = outcome(CURRENT_KEY, Optional.empty());

        assertThatIllegalStateException()
                .isThrownBy(() -> coordinator.completeBenchmark(validOutcome));

        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();
        assertThat(plan.action())
                .isEqualTo(GraphicsQualityStartupDecision.Action.RUN_BENCHMARK);
        assertThat(coordinator.completeBenchmark(validOutcome)).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThatIllegalStateException()
                .isThrownBy(() -> coordinator.completeBenchmark(validOutcome));
        assertThatIllegalStateException().isThrownBy(coordinator::begin);
    }

    @Test
    void malformedPersistedStateRemainsVisibleToCaller() throws IOException {
        Path stateFile = tempDirectory.resolve(GraphicsQualityStateStore.FILE_NAME);
        assertThat(
                        Files.writeString(
                                stateFile, "not-a-valid-state\n", StandardCharsets.UTF_8))
                .isEqualTo(stateFile);
        GraphicsQualityStartupCoordinator coordinator = coordinator();

        assertThatThrownBy(coordinator::begin)
                .isInstanceOf(GraphicsQualityStateStore.MalformedStateException.class);
    }

    private GraphicsQualityStartupCoordinator coordinator() {
        return new GraphicsQualityStartupCoordinator(tempDirectory, CURRENT_KEY);
    }

    private static GraphicsBenchmarkSession.Outcome outcome(
            GraphicsBenchmarkCompatibilityKey compatibilityKey,
            Optional<GraphicsQualityState> previousState) {
        GraphicsBenchmarkSession session =
                new GraphicsBenchmarkSession(
                        new GraphicsBenchmarkSession.Config(
                                COMMIT,
                                compatibilityKey,
                                GraphicsQualityPreset.MEDIUM,
                                1920,
                                1080,
                                1.0d,
                                0,
                                1),
                        previousState);
        return session.accept(sample()).orElseThrow();
    }

    private static GraphicsTelemetrySample sample() {
        return new GraphicsTelemetrySample(
                10_000_000L, OptionalLong.empty(), 256L, 32, 64);
    }
}
