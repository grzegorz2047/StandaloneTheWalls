package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.LowProfileMapBudget;

class LowProfileMapBudgetCompatibilityTest {
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    @Test
    void lowPresetCoversTheMinimalMapMemoryAndLightGuardrails() {
        long lowTextureBudgetBytes =
                Math.multiplyExact(
                        GraphicsQualityPreset.LOW.textureMemoryBudgetMib(), BYTES_PER_MIB);

        assertThat(lowTextureBudgetBytes)
                .isGreaterThanOrEqualTo(LowProfileMapBudget.MAX_UNCOMPRESSED_MEMBER_BYTES);
        assertThat(GraphicsQualityPreset.LOW.maximumDynamicLights())
                .isGreaterThanOrEqualTo(LowProfileMapBudget.MAX_SCENE_LIGHTS);
    }
}
