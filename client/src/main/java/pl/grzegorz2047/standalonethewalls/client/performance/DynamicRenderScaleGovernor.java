package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/** Stateful one-way render-scale governor that ignores isolated frame-time spikes. */
public final class DynamicRenderScaleGovernor {
    private static final int MAXIMUM_REQUIRED_WINDOWS = 120;
    private static final int SCALE_UNITS_PER_ONE = 10_000;

    private final int minimumScaleUnits;
    private final int maximumScaleUnits;
    private final int reductionStepUnits;
    private final int requiredConsecutiveOverBudgetWindows;
    private int currentScaleUnits;
    private int consecutiveOverBudgetWindows;

    public DynamicRenderScaleGovernor(
            GraphicsQualityPreset preset,
            double reductionStep,
            int requiredConsecutiveOverBudgetWindows) {
        Objects.requireNonNull(preset, "preset");
        if (requiredConsecutiveOverBudgetWindows < 1
                || requiredConsecutiveOverBudgetWindows > MAXIMUM_REQUIRED_WINDOWS) {
            throw new IllegalArgumentException(
                    "requiredConsecutiveOverBudgetWindows is outside the bounded range");
        }
        if (!Double.isFinite(reductionStep) || reductionStep <= 0.0d || reductionStep > 1.0d) {
            throw new IllegalArgumentException("reductionStep is outside the bounded range");
        }

        minimumScaleUnits = toScaleUnits(preset.minimumRenderScale(), "minimumRenderScale");
        maximumScaleUnits = toScaleUnits(preset.maximumRenderScale(), "maximumRenderScale");
        currentScaleUnits = toScaleUnits(preset.defaultRenderScale(), "defaultRenderScale");
        reductionStepUnits = toScaleUnits(reductionStep, "reductionStep");
        if (minimumScaleUnits > maximumScaleUnits
                || currentScaleUnits < minimumScaleUnits
                || currentScaleUnits > maximumScaleUnits) {
            throw new IllegalArgumentException("preset render-scale bounds are inconsistent");
        }
        this.requiredConsecutiveOverBudgetWindows = requiredConsecutiveOverBudgetWindows;
    }

    public synchronized double observe(long observedP95Nanos, long budgetNanos) {
        requireFrameTime(observedP95Nanos, "observedP95Nanos");
        requireFrameTime(budgetNanos, "budgetNanos");

        if (observedP95Nanos <= budgetNanos || atMinimumScale()) {
            consecutiveOverBudgetWindows = 0;
            return currentRenderScale();
        }

        consecutiveOverBudgetWindows++;
        if (consecutiveOverBudgetWindows < requiredConsecutiveOverBudgetWindows) {
            return currentRenderScale();
        }

        currentScaleUnits = Math.max(minimumScaleUnits, currentScaleUnits - reductionStepUnits);
        consecutiveOverBudgetWindows = 0;
        return currentRenderScale();
    }

    public synchronized double currentRenderScale() {
        return (double) currentScaleUnits / SCALE_UNITS_PER_ONE;
    }

    public synchronized int consecutiveOverBudgetWindows() {
        return consecutiveOverBudgetWindows;
    }

    public synchronized boolean atMinimumScale() {
        return currentScaleUnits == minimumScaleUnits;
    }

    private static int toScaleUnits(double scale, String field) {
        if (!Double.isFinite(scale) || scale <= 0.0d || scale > 1.0d) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
        long units = Math.round(scale * SCALE_UNITS_PER_ONE);
        if (units < 1L || units > SCALE_UNITS_PER_ONE) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
        double reconstructed = (double) units / SCALE_UNITS_PER_ONE;
        if (Math.abs(reconstructed - scale) > 0.000_000_001d) {
            throw new IllegalArgumentException(field + " exceeds supported precision");
        }
        return Math.toIntExact(units);
    }

    private static void requireFrameTime(long value, String field) {
        if (value <= 0L || value > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
    }
}
