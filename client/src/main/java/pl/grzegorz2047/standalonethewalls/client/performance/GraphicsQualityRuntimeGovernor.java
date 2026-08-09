package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/**
 * Coordinates render-scale reduction before allowing one-way preset degradation.
 */
public final class GraphicsQualityRuntimeGovernor {
    private final double renderScaleReductionStep;
    private final int requiredRenderScaleOverBudgetWindows;
    private final QualityDowngradeGovernor presetDowngradeGovernor;
    private GraphicsQualityPreset currentPreset;
    private DynamicRenderScaleGovernor renderScaleGovernor;

    public GraphicsQualityRuntimeGovernor(
            GraphicsQualityPreset initialPreset,
            double renderScaleReductionStep,
            int requiredRenderScaleOverBudgetWindows,
            int requiredPresetOverBudgetWindows) {
        currentPreset = Objects.requireNonNull(initialPreset, "initialPreset");
        this.renderScaleReductionStep = renderScaleReductionStep;
        this.requiredRenderScaleOverBudgetWindows = requiredRenderScaleOverBudgetWindows;
        renderScaleGovernor =
                new DynamicRenderScaleGovernor(
                        currentPreset,
                        renderScaleReductionStep,
                        requiredRenderScaleOverBudgetWindows);
        presetDowngradeGovernor =
                new QualityDowngradeGovernor(requiredPresetOverBudgetWindows);
    }

    public synchronized Snapshot observe(long observedP95Nanos, long budgetNanos) {
        boolean wasAtMinimumScale = renderScaleGovernor.atMinimumScale();
        double observedRenderScale = renderScaleGovernor.observe(observedP95Nanos, budgetNanos);

        if (observedP95Nanos <= budgetNanos) {
            GraphicsQualityPreset stablePreset =
                    presetDowngradeGovernor.observe(
                            currentPreset, observedP95Nanos, budgetNanos);
            if (stablePreset != currentPreset) {
                throw new IllegalStateException("healthy observation changed the quality preset");
            }
            return snapshot(observedRenderScale);
        }
        if (!wasAtMinimumScale) {
            return snapshot(observedRenderScale);
        }

        GraphicsQualityPreset observedPreset =
                presetDowngradeGovernor.observe(currentPreset, observedP95Nanos, budgetNanos);
        if (observedPreset != currentPreset) {
            currentPreset = observedPreset;
            renderScaleGovernor =
                    new DynamicRenderScaleGovernor(
                            currentPreset,
                            renderScaleReductionStep,
                            requiredRenderScaleOverBudgetWindows);
            return snapshot();
        }
        return snapshot(observedRenderScale);
    }

    public synchronized Snapshot snapshot() {
        return snapshot(renderScaleGovernor.currentRenderScale());
    }

    private Snapshot snapshot(double renderScale) {
        return new Snapshot(
                currentPreset,
                renderScale,
                renderScaleGovernor.atMinimumScale(),
                renderScaleGovernor.consecutiveOverBudgetWindows(),
                presetDowngradeGovernor.consecutiveOverBudgetWindows());
    }

    public record Snapshot(
            GraphicsQualityPreset preset,
            double renderScale,
            boolean minimumRenderScale,
            int renderScaleOverBudgetWindows,
            int presetOverBudgetWindows) {
        public Snapshot {
            Objects.requireNonNull(preset, "preset");
            if (!Double.isFinite(renderScale) || renderScale <= 0.0d || renderScale > 1.0d) {
                throw new IllegalArgumentException("renderScale is outside the bounded range");
            }
            if (renderScaleOverBudgetWindows < 0 || presetOverBudgetWindows < 0) {
                throw new IllegalArgumentException("over-budget streak is negative");
            }
        }
    }
}
