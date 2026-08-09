package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic warm-up and measurement lifecycle for one local graphics benchmark run. */
public final class GraphicsBenchmarkSession {
    public static final int MAXIMUM_WARM_UP_FRAMES = 4_096;

    private final Config config;
    private final Optional<GraphicsQualityState> previousState;
    private final List<GraphicsTelemetrySample> measurementSamples;
    private int acceptedFrameCount;
    private Outcome outcome;

    public GraphicsBenchmarkSession(Config config, Optional<GraphicsQualityState> previousState) {
        this.config = Objects.requireNonNull(config, "config");
        this.previousState = Objects.requireNonNull(previousState, "previousState");
        this.measurementSamples = new ArrayList<>(config.measurementFrameCount());
    }

    public Phase phase() {
        if (outcome != null) {
            return Phase.COMPLETE;
        }
        if (acceptedFrameCount < config.warmUpFrameCount()) {
            return Phase.WARM_UP;
        }
        return Phase.MEASURING;
    }

    public int warmUpFramesRemaining() {
        return Math.max(0, config.warmUpFrameCount() - acceptedFrameCount);
    }

    public int measurementFramesCollected() {
        return measurementSamples.size();
    }

    public int measurementFramesRemaining() {
        return config.measurementFrameCount() - measurementSamples.size();
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public Optional<Outcome> accept(GraphicsTelemetrySample sample) {
        Objects.requireNonNull(sample, "sample");
        if (outcome != null) {
            throw new IllegalStateException("benchmark session is already complete");
        }
        if (acceptedFrameCount < config.warmUpFrameCount()) {
            acceptedFrameCount++;
            return Optional.empty();
        }

        measurementSamples.add(sample);
        acceptedFrameCount++;
        if (measurementSamples.size() < config.measurementFrameCount()) {
            return Optional.empty();
        }

        GraphicsTelemetrySummary summary =
                GraphicsTelemetrySummary.fromSamples(measurementSamples);
        GraphicsBenchmarkResult result =
                BenchmarkQualitySelector.select(
                        summary.cpuFrameTime(),
                        config.width(),
                        config.height(),
                        config.renderScale());
        GraphicsBenchmarkCompatibilityKey compatibilityKey = config.compatibilityKey();
        GraphicsBenchmarkReport report =
                new GraphicsBenchmarkReport(
                        config.repositoryCommit(),
                        compatibilityKey.assetPackId(),
                        compatibilityKey.assetPackVersion(),
                        compatibilityKey.scenarioId(),
                        compatibilityKey.scenarioVersion(),
                        config.measuredPreset(),
                        result);
        GraphicsQualityState qualityState =
                previousState
                        .map(
                                state ->
                                        state.refreshRecommendation(
                                                compatibilityKey, result.recommendedPreset()))
                        .orElseGet(
                                () ->
                                        new GraphicsQualityState(
                                                compatibilityKey,
                                                result.recommendedPreset(),
                                                Optional.empty()));
        outcome = new Outcome(report, summary, qualityState);
        return Optional.of(outcome);
    }

    public record Config(
            String repositoryCommit,
            GraphicsBenchmarkCompatibilityKey compatibilityKey,
            GraphicsQualityPreset measuredPreset,
            int width,
            int height,
            double renderScale,
            int warmUpFrameCount,
            int measurementFrameCount) {
        public Config {
            Objects.requireNonNull(repositoryCommit, "repositoryCommit");
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
            Objects.requireNonNull(measuredPreset, "measuredPreset");
            if (warmUpFrameCount < 0 || warmUpFrameCount > MAXIMUM_WARM_UP_FRAMES) {
                throw new IllegalArgumentException("warmUpFrameCount is outside the bounded range");
            }
            if (measurementFrameCount < 1
                    || measurementFrameCount > FrameTimeStatistics.MAXIMUM_SAMPLES) {
                throw new IllegalArgumentException(
                        "measurementFrameCount is outside the bounded range");
            }
        }
    }

    public record Outcome(
            GraphicsBenchmarkReport report,
            GraphicsTelemetrySummary telemetrySummary,
            GraphicsQualityState qualityState) {
        public Outcome {
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(telemetrySummary, "telemetrySummary");
            Objects.requireNonNull(qualityState, "qualityState");
            if (!report.result().statistics().equals(telemetrySummary.cpuFrameTime())) {
                throw new IllegalArgumentException(
                        "benchmark report CPU statistics do not match telemetry summary");
            }
            if (!qualityState.compatibilityKey().equals(GraphicsBenchmarkCompatibilityKey.fromReport(report))) {
                throw new IllegalArgumentException(
                        "benchmark report and quality state compatibility keys do not match");
            }
            if (qualityState.recommendedPreset() != report.result().recommendedPreset()) {
                throw new IllegalArgumentException(
                        "benchmark report and quality state recommendations do not match");
            }
        }
    }

    public enum Phase {
        WARM_UP,
        MEASURING,
        COMPLETE
    }
}
