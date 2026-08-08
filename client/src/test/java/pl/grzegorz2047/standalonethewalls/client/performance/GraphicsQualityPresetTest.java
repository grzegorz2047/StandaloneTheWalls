package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphicsQualityPresetTest {
    @Test
    void keepsEveryPresetInsideSafeBoundedRanges() {
        for (GraphicsQualityPreset preset : GraphicsQualityPreset.values()) {
            assertThat(preset.minimumRenderScale()).isBetween(0.50d, 1.00d);
            assertThat(preset.defaultRenderScale())
                    .isBetween(preset.minimumRenderScale(), preset.maximumRenderScale());
            assertThat(preset.maximumRenderScale()).isBetween(0.50d, 1.00d);
            assertThat(preset.maximumShadowMapSize()).isBetween(256, 4096);
            assertThat(preset.lodBias()).isBetween(0.50d, 2.00d);
            assertThat(preset.vegetationDensity()).isBetween(0.0d, 1.0d);
            assertThat(preset.maximumDynamicLights()).isBetween(0, 32);
            assertThat(preset.particleBudget()).isBetween(0, 10_000);
            assertThat(preset.anisotropy()).isBetween(1, 16);
            assertThat(preset.textureMemoryBudgetMib()).isBetween(128, 4_096);
        }
    }

    @Test
    void exposesOnlyOneWayDowngrades() {
        assertThat(GraphicsQualityPreset.HIGH.lower()).contains(GraphicsQualityPreset.MEDIUM);
        assertThat(GraphicsQualityPreset.MEDIUM.lower()).contains(GraphicsQualityPreset.LOW);
        assertThat(GraphicsQualityPreset.LOW.lower()).isEmpty();
    }
}
