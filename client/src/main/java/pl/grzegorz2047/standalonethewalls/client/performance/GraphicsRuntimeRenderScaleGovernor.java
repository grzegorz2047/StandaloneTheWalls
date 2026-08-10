package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Non-overlapping runtime p95 windows driving one-way render-scale reductions. */
final class GraphicsRuntimeRenderScaleGovernor {
    static final int WINDOW_SAMPLE_COUNT = 120;
    static final double REDUCTION_STEP = 0.05d;
    static final int REQUIRED_OVER_BUDGET_WINDOWS = 3;

    private final long p95BudgetNanos;
    private final int windowSampleCount;
    private final DynamicRenderScaleGovernor governor;
    private final List<Long> frameTimeWindow;

    GraphicsRuntimeRenderScaleGovernor(GraphicsQualityPreset preset) {
        this(preset, WINDOW_SAMPLE_COUNT, REDUCTION_STEP, REQUIRED_OVER_BUDGET_WINDOWS);
    }

    GraphicsRuntimeRenderScaleGovernor(
            GraphicsQualityPreset preset,
            int windowSampleCount,
            double reductionStep,
            int requiredOverBudgetWindows) {
        GraphicsQualityPreset checkedPreset = Objects.requireNonNull(preset, "preset");
        if (windowSampleCount < 1 || windowSampleCount > FrameTimeStatistics.MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException(
                    "runtime frame-time window is outside the bounded range");
        }
        this.p95BudgetNanos =
                checkedPreset == GraphicsQualityPreset.LOW
                        ? BenchmarkQualitySelector.MINIMUM_TARGET_P95_NANOS
                        : BenchmarkQualitySelector.PRIMARY_TARGET_P95_NANOS;
        this.windowSampleCount = windowSampleCount;
        this.governor =
                new DynamicRenderScaleGovernor(
                        checkedPreset, reductionStep, requiredOverBudgetWindows);
        this.frameTimeWindow = new ArrayList<>(windowSampleCount);
    }

    OptionalDouble acceptFrameTime(long frameTimeNanos) {
        if (frameTimeNanos <= 0L || frameTimeNanos > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException("runtime frame time is outside the bounded range");
        }
        frameTimeWindow.add(frameTimeNanos);
        if (frameTimeWindow.size() < windowSampleCount) {
            return OptionalDouble.empty();
        }

        FrameTimeStatistics statistics = FrameTimeStatistics.fromNanos(frameTimeWindow);
        frameTimeWindow.clear();
        double previousScale = governor.currentRenderScale();
        double currentScale = governor.observe(statistics.p95Nanos(), p95BudgetNanos);
        if (Double.compare(previousScale, currentScale) == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(currentScale);
    }

    double currentRenderScale() {
        return governor.currentRenderScale();
    }

    int pendingSampleCount() {
        return frameTimeWindow.size();
    }

    int consecutiveOverBudgetWindows() {
        return governor.consecutiveOverBudgetWindows();
    }

    long p95BudgetNanos() {
        return p95BudgetNanos;
    }
}
