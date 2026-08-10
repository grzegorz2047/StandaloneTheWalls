package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class GraphicsPresetRendererSmokeMainTest {
    @Test
    void rejectsMissingUnknownPresetAndUnknownOptionWithoutStartingRenderer() {
        assertThat(GraphicsPresetRendererSmokeMain.run(new String[0]))
                .isEqualTo(GraphicsPresetRendererSmokeMain.EXIT_USAGE);
        assertThat(GraphicsPresetRendererSmokeMain.run(new String[] {"unsupported"}))
                .isEqualTo(GraphicsPresetRendererSmokeMain.EXIT_USAGE);
        assertThat(GraphicsPresetRendererSmokeMain.run(new String[] {"LOW", "--unsupported"}))
                .isEqualTo(GraphicsPresetRendererSmokeMain.EXIT_USAGE);
    }

    @Test
    void acceptsExpectedPreferredSnapshotsForAllPresets() {
        for (GraphicsQualityPreset preset : GraphicsQualityPreset.values()) {
            GraphicsPresetRendererSmokeApplication.Snapshot snapshot = snapshot(preset, false);

            assertThatCode(
                            () ->
                                    GraphicsPresetRendererSmokeMain.validateSnapshot(
                                            preset, false, snapshot))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsExpectedFallbackSnapshot() {
        GraphicsPresetRendererSmokeApplication.Snapshot snapshot =
                snapshot(GraphicsQualityPreset.LOW, true);

        assertThatCode(
                        () ->
                                GraphicsPresetRendererSmokeMain.validateSnapshot(
                                        GraphicsQualityPreset.LOW, true, snapshot))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSnapshotUsingWrongFramebufferOrMaterialPath() {
        GraphicsPresetRendererSmokeApplication.Snapshot wrongFramebuffer =
                new GraphicsPresetRendererSmokeApplication.Snapshot(
                        GraphicsQualityPreset.LOW,
                        GraphicsQualityPreset.LOW.defaultRenderScale(),
                        false,
                        false,
                        GraphicsBenchmarkReferenceScene.geometryCount(GraphicsQualityPreset.LOW),
                        2);
        GraphicsPresetRendererSmokeApplication.Snapshot wrongMaterialPath =
                snapshot(GraphicsQualityPreset.LOW, true);

        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsPresetRendererSmokeMain.validateSnapshot(
                                        GraphicsQualityPreset.LOW, false, wrongFramebuffer));
        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                GraphicsPresetRendererSmokeMain.validateSnapshot(
                                        GraphicsQualityPreset.LOW, false, wrongMaterialPath));
    }

    private static GraphicsPresetRendererSmokeApplication.Snapshot snapshot(
            GraphicsQualityPreset preset, boolean fallbackUsed) {
        boolean offscreen =
                GraphicsBenchmarkRenderScale.requiresOffscreenRendering(preset.defaultRenderScale());
        return new GraphicsPresetRendererSmokeApplication.Snapshot(
                preset,
                preset.defaultRenderScale(),
                offscreen,
                fallbackUsed,
                GraphicsBenchmarkReferenceScene.geometryCount(preset),
                2);
    }
}
