package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkSessionTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final GraphicsBenchmarkCompatibilityKey KEY =
            new GraphicsBenchmarkCompatibilityKey("core", "8", "first-run", 3);

    @Test
    void discardsWarmUpAndCompletesOnExactMeasurementFrame() {
        GraphicsQualityState previous =
                new GraphicsQualityState(
                        new GraphicsBenchmarkCompatibilityKey("core", "7", "first-run", 2),
                        GraphicsQualityPreset.LOW,
                        Optional.of(GraphicsQualityPreset.HIGH));
        GraphicsBenchmarkSession session =
                new GraphicsBenchmarkSession(config(2, 4), Optional.of(previous));

        assertThat(session.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.WARM_UP);
        assertThat(session.warmUpFramesRemaining()).isEqualTo(2);
        assertThat(session.accept(sample(1_000_000_000L, 99_999L, 999, 999))).isEmpty();
        assertThat(session.accept(sample(2_000_000_000L, 88_888L, 888, 888))).isEmpty();
        assertThat(session.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.MEASURING);
        assertThat(session.warmUpFramesRemaining()).isZero();

        assertThat(session.accept(sample(10_000_000L, 100L, 10, 100))).isEmpty();
        assertThat(session.accept(sample(12_000_000L, 200L, 20, 200))).isEmpty();
        assertThat(session.accept(sample(14_000_000L, 300L, 30, 300))).isEmpty();
        GraphicsBenchmarkSession.Outcome outcome =
                session.accept(sample(16_000_000L, 400L, 40, 400)).orElseThrow();

        assertThat(session.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.COMPLETE);
        assertThat(session.measurementFramesCollected()).isEqualTo(4);
        assertThat(session.measurementFramesRemaining()).isZero();
        assertThat(session.outcome()).contains(outcome);
        assertThat(outcome.telemetrySummary().cpuFrameTime())
                .isEqualTo(new FrameTimeStatistics(4, 13_000_000L, 16_000_000L, 16_000_000L));
        assertThat(outcome.telemetrySummary().peakResidentMemoryBytes()).isEqualTo(400L);
        assertThat(outcome.telemetrySummary().peakDrawCalls()).isEqualTo(40);
        assertThat(outcome.telemetrySummary().peakRenderedObjectCount()).isEqualTo(400);
        assertThat(outcome.report().telemetrySummary()).isEqualTo(outcome.telemetrySummary());
        assertThat(outcome.report().repositoryCommit()).isEqualTo(COMMIT);
        assertThat(outcome.report().assetPackVersion()).isEqualTo("8");
        assertThat(outcome.report().scenarioVersion()).isEqualTo(3);
        assertThat(outcome.report().measuredPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(outcome.report().result().recommendedPreset())
                .isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(outcome.report().result().targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET);
        assertThat(outcome.qualityState().compatibilityKey()).isEqualTo(KEY);
        assertThat(outcome.qualityState().recommendedPreset())
                .isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(outcome.qualityState().manualOverride()).contains(GraphicsQualityPreset.HIGH);
        assertThatIllegalStateException()
                .isThrownBy(() -> session.accept(sample(10_000_000L, 1L, 1, 1)));
    }

    @Test
    void newSessionWithoutPreviousStateHasNoManualOverride() {
        GraphicsBenchmarkSession session =
                new GraphicsBenchmarkSession(config(0, 1), Optional.empty());

        GraphicsBenchmarkSession.Outcome outcome =
                session.accept(sample(20_000_000L, 64L, 5, 6)).orElseThrow();

        assertThat(outcome.qualityState().manualOverride()).isEmpty();
        assertThat(outcome.qualityState().recommendedPreset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(outcome.report().telemetrySummary()).isEqualTo(outcome.telemetrySummary());
        assertThat(outcome.report().result().targetStatus())
                .isEqualTo(GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET);
    }

    @Test
    void rejectsInvalidBoundsNullsAndForgedOutcome() {
        assertThatIllegalArgumentException().isThrownBy(() -> config(-1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> config(GraphicsBenchmarkSession.MAXIMUM_WARM_UP_FRAMES + 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> config(0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> config(0, FrameTimeStatistics.MAXIMUM_SAMPLES + 1));
        assertThatNullPointerException()
                .isThrownBy(() -> new GraphicsBenchmarkSession(config(0, 1), null));

        GraphicsBenchmarkSession session =
                new GraphicsBenchmarkSession(config(0, 1), Optional.empty());
        assertThatNullPointerException().isThrownBy(() -> session.accept(null));

        GraphicsTelemetrySummary telemetrySummary =
                GraphicsTelemetrySummary.fromSamples(
                        java.util.List.of(sample(20_000_000L, 64L, 5, 6)));
        GraphicsBenchmarkResult differentResult =
                BenchmarkQualitySelector.select(
                        new FrameTimeStatistics(1, 10_000_000L, 10_000_000L, 10_000_000L),
                        1920,
                        1080,
                        1.0d);
        GraphicsTelemetrySummary differentTelemetry =
                new GraphicsTelemetrySummary(
                        1, differentResult.statistics(), Optional.empty(), 0, 32L, 2, 3);
        GraphicsBenchmarkReport report =
                new GraphicsBenchmarkReport(
                        COMMIT,
                        "core",
                        "8",
                        "first-run",
                        3,
                        GraphicsQualityPreset.MEDIUM,
                        differentResult,
                        differentTelemetry);
        GraphicsQualityState state =
                new GraphicsQualityState(
                        KEY, differentResult.recommendedPreset(), Optional.empty());
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkSession.Outcome(
                                        report, telemetrySummary, state));
    }

    private static GraphicsBenchmarkSession.Config config(
            int warmUpFrameCount, int measurementFrameCount) {
        return new GraphicsBenchmarkSession.Config(
                COMMIT,
                KEY,
                GraphicsQualityPreset.MEDIUM,
                1920,
                1080,
                1.0d,
                warmUpFrameCount,
                measurementFrameCount);
    }

    private static GraphicsTelemetrySample sample(
            long cpuNanos, long memoryBytes, int drawCalls, int objectCount) {
        return new GraphicsTelemetrySample(
                cpuNanos, OptionalLong.empty(), memoryBytes, drawCalls, objectCount);
    }
}
