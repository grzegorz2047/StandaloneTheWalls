package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SUNDERFRONT_REAL_RENDERER_SMOKE", matches = "1")
class GraphicsPresetRendererSmokeDisplayTest {
    @Test
    void allPresetsCompleteRealRendererFrames() {
        assertThat(GraphicsPresetRendererSmokeMain.run()).isZero();
    }
}
