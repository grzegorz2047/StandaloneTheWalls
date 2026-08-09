package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Coordinates one startup quality-state decision and an optional benchmark persistence step. */
public final class GraphicsQualityStartupCoordinator {
    private final GraphicsQualityStateStore stateStore;
    private final GraphicsBenchmarkReportStore reportStore;
    private final GraphicsBenchmarkCompatibilityKey currentKey;
    private StartupPlan plan;
    private boolean benchmarkCompleted;

    public GraphicsQualityStartupCoordinator(
            Path dataDirectory, GraphicsBenchmarkCompatibilityKey currentKey) {
        this(
                new GraphicsQualityStateStore(dataDirectory),
                new GraphicsBenchmarkReportStore(dataDirectory),
                currentKey);
    }

    GraphicsQualityStartupCoordinator(
            GraphicsQualityStateStore stateStore,
            GraphicsBenchmarkReportStore reportStore,
            GraphicsBenchmarkCompatibilityKey currentKey) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
        this.currentKey = Objects.requireNonNull(currentKey, "currentKey");
    }

    public StartupPlan begin() throws IOException {
        if (plan != null) {
            throw new IllegalStateException("graphics quality startup has already begun");
        }
        Optional<GraphicsQualityState> persistedState = stateStore.load();
        GraphicsQualityStartupDecision decision =
                GraphicsQualityStartupDecision.evaluate(persistedState, currentKey);
        plan =
                switch (decision.action()) {
                    case RUN_BENCHMARK ->
                            new StartupPlan(decision.action(), Optional.empty(), persistedState);
                    case USE_PERSISTED_PRESET ->
                            new StartupPlan(decision.action(), decision.preset(), Optional.empty());
                };
        return plan;
    }

    public GraphicsQualityPreset completeBenchmark(GraphicsBenchmarkSession.Outcome outcome)
            throws IOException {
        Objects.requireNonNull(outcome, "outcome");
        if (plan == null) {
            throw new IllegalStateException("graphics quality startup has not begun");
        }
        if (plan.action() != GraphicsQualityStartupDecision.Action.RUN_BENCHMARK) {
            throw new IllegalStateException("graphics quality startup did not require a benchmark");
        }
        if (benchmarkCompleted) {
            throw new IllegalStateException(
                    "graphics quality benchmark has already been persisted");
        }
        GraphicsQualityState qualityState = outcome.qualityState();
        if (!qualityState.compatibilityKey().equals(currentKey)) {
            throw new IllegalArgumentException(
                    "benchmark outcome compatibility key does not match startup key");
        }

        reportStore.save(outcome.report());
        stateStore.save(qualityState);
        benchmarkCompleted = true;
        return qualityState.effectivePreset();
    }

    public record StartupPlan(
            GraphicsQualityStartupDecision.Action action,
            Optional<GraphicsQualityPreset> effectivePreset,
            Optional<GraphicsQualityState> benchmarkPreviousState) {
        public StartupPlan {
            Objects.requireNonNull(action, "action");
            effectivePreset = Objects.requireNonNull(effectivePreset, "effectivePreset");
            benchmarkPreviousState =
                    Objects.requireNonNull(benchmarkPreviousState, "benchmarkPreviousState");
            if (action == GraphicsQualityStartupDecision.Action.RUN_BENCHMARK) {
                if (effectivePreset.isPresent()) {
                    throw new IllegalArgumentException(
                            "benchmark-required startup plan cannot expose an effective preset");
                }
            } else if (effectivePreset.isEmpty() || benchmarkPreviousState.isPresent()) {
                throw new IllegalArgumentException(
                        "persisted startup plan must expose only an effective preset");
            }
        }
    }
}
