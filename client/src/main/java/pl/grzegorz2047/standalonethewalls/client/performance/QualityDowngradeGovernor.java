package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/** Stateful one-way quality governor that ignores isolated frame-time spikes. */
public final class QualityDowngradeGovernor {
    private static final int MAXIMUM_REQUIRED_WINDOWS = 120;

    private final int requiredConsecutiveOverBudgetWindows;
    private int consecutiveOverBudgetWindows;

    public QualityDowngradeGovernor(int requiredConsecutiveOverBudgetWindows) {
        if (requiredConsecutiveOverBudgetWindows < 1
                || requiredConsecutiveOverBudgetWindows > MAXIMUM_REQUIRED_WINDOWS) {
            throw new IllegalArgumentException(
                    "requiredConsecutiveOverBudgetWindows is outside the bounded range");
        }
        this.requiredConsecutiveOverBudgetWindows = requiredConsecutiveOverBudgetWindows;
    }

    public synchronized GraphicsQualityPreset observe(
            GraphicsQualityPreset currentPreset, long observedP95Nanos, long budgetNanos) {
        Objects.requireNonNull(currentPreset, "currentPreset");
        requireFrameTime(observedP95Nanos, "observedP95Nanos");
        requireFrameTime(budgetNanos, "budgetNanos");

        if (observedP95Nanos <= budgetNanos) {
            consecutiveOverBudgetWindows = 0;
            return currentPreset;
        }
        if (currentPreset == GraphicsQualityPreset.LOW) {
            consecutiveOverBudgetWindows = 0;
            return currentPreset;
        }

        consecutiveOverBudgetWindows++;
        if (consecutiveOverBudgetWindows < requiredConsecutiveOverBudgetWindows) {
            return currentPreset;
        }

        consecutiveOverBudgetWindows = 0;
        return currentPreset.lower().orElse(currentPreset);
    }

    public synchronized int consecutiveOverBudgetWindows() {
        return consecutiveOverBudgetWindows;
    }

    private static void requireFrameTime(long value, String field) {
        if (value <= 0L || value > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
    }
}
