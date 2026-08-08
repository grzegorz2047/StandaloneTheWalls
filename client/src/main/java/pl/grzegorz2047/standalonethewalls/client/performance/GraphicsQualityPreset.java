package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Optional;

/**
 * Renderer-independent quality budgets used by benchmark selection and later client integration.
 */
public enum GraphicsQualityPreset {
    LOW(0.75d, 0.67d, 1.00d, 1024, 0.75d, 0.50d, 4, false, 1_000, 2, 512),
    MEDIUM(1.00d, 0.75d, 1.00d, 2048, 1.00d, 0.75d, 8, true, 2_500, 4, 1_024),
    HIGH(1.00d, 0.85d, 1.00d, 4096, 1.25d, 1.00d, 16, true, 5_000, 8, 2_048);

    private final double defaultRenderScale;
    private final double minimumRenderScale;
    private final double maximumRenderScale;
    private final int maximumShadowMapSize;
    private final double lodBias;
    private final double vegetationDensity;
    private final int maximumDynamicLights;
    private final boolean postProcessingEnabled;
    private final int particleBudget;
    private final int anisotropy;
    private final int textureMemoryBudgetMib;

    GraphicsQualityPreset(
            double defaultRenderScale,
            double minimumRenderScale,
            double maximumRenderScale,
            int maximumShadowMapSize,
            double lodBias,
            double vegetationDensity,
            int maximumDynamicLights,
            boolean postProcessingEnabled,
            int particleBudget,
            int anisotropy,
            int textureMemoryBudgetMib) {
        this.defaultRenderScale = defaultRenderScale;
        this.minimumRenderScale = minimumRenderScale;
        this.maximumRenderScale = maximumRenderScale;
        this.maximumShadowMapSize = maximumShadowMapSize;
        this.lodBias = lodBias;
        this.vegetationDensity = vegetationDensity;
        this.maximumDynamicLights = maximumDynamicLights;
        this.postProcessingEnabled = postProcessingEnabled;
        this.particleBudget = particleBudget;
        this.anisotropy = anisotropy;
        this.textureMemoryBudgetMib = textureMemoryBudgetMib;
    }

    public double defaultRenderScale() {
        return defaultRenderScale;
    }

    public double minimumRenderScale() {
        return minimumRenderScale;
    }

    public double maximumRenderScale() {
        return maximumRenderScale;
    }

    public int maximumShadowMapSize() {
        return maximumShadowMapSize;
    }

    public double lodBias() {
        return lodBias;
    }

    public double vegetationDensity() {
        return vegetationDensity;
    }

    public int maximumDynamicLights() {
        return maximumDynamicLights;
    }

    public boolean postProcessingEnabled() {
        return postProcessingEnabled;
    }

    public int particleBudget() {
        return particleBudget;
    }

    public int anisotropy() {
        return anisotropy;
    }

    public int textureMemoryBudgetMib() {
        return textureMemoryBudgetMib;
    }

    public Optional<GraphicsQualityPreset> lower() {
        return switch (this) {
            case HIGH -> Optional.of(MEDIUM);
            case MEDIUM -> Optional.of(LOW);
            case LOW -> Optional.empty();
        };
    }
}
