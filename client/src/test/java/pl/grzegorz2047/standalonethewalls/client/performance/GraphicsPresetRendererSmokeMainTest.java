package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class GraphicsPresetRendererSmokeMainTest {
    @Test
    void acceptsExpectedSnapshotsForAllPresets() {
        for (GraphicsQualityPreset preset : GraphicsQualityPreset.values()) {
            boolean offscreen =
                    GraphicsBenchmarkRenderScale.requiresOffscreenRendering(
                            preset.defaultRenderScale());
            GraphicsPresetRendererSmokeApplication.Snapshot snapshot =
                    new GraphicsPresetRendererSmokeApplication.Snapshot(
                            preset,
                            preset.defaultRenderScale(),
                            offscreen,
                            GraphicsBenchmarkReferenceScene.geometryCount(preset),
                            2);

            assertThatCode(() -> GraphicsPresetRendererSmokeMain.validateSnapshot(preset, snapshot))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsSnapshotUsingWrongFramebufferPath() {
        GraphicsPresetRendererSmokeApplication.Snapshot snapshot =
                new GraphicsPresetRendererSmokeApplication.Snapshot(
                        GraphicsQualityPreset.LOW,
                        GraphicsQualityPreset.LOW.defaultRenderScale(),
                        false,
                        GraphicsBenchmarkReferenceScene.geometryCount(GraphicsQualityPreset.LOW),
                        2);

        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsPresetRendererSmokeMain.validateSnapshot(
                                        GraphicsQualityPreset.LOW, snapshot));
    }
}
