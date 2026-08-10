package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GraphicsBenchmarkRenderScaleTest {
    @Test
    void computesDeterministicPresetBoundaryDimensions() {
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1280, 720, 0.67d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(858, 482));
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1280, 720, 0.75d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(960, 540));
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1920, 1080, 0.75d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(1440, 810));
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1920, 1080, 0.85d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(1632, 918));
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1920, 1080, 1.0d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(1920, 1080));
    }

    @Test
    void distinguishesDirectRenderFromScaledFramebuffer() {
        assertThat(GraphicsBenchmarkRenderScale.requiresOffscreenRendering(1.0d)).isFalse();
        assertThat(GraphicsBenchmarkRenderScale.requiresOffscreenRendering(0.999d)).isTrue();
    }

    @Test
    void clampsTinyPositiveScaledDimensionsToOnePixel() {
        assertThat(GraphicsBenchmarkRenderScale.scaledDimensions(1, 1, 0.01d))
                .isEqualTo(new GraphicsBenchmarkRenderScale.Dimensions(1, 1));
    }

    @Test
    void rejectsInvalidScaleAndDisplayBounds() {
        assertInvalidDimensions(0, 720, 0.75d);
        assertInvalidDimensions(1280, 0, 0.75d);
        assertInvalidDimensions(1280, 720, Double.NaN);
        assertInvalidDimensions(1280, 720, 0.0d);
        assertInvalidDimensions(1280, 720, 1.01d);
    }

    private static void assertInvalidDimensions(int width, int height, double renderScale) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkRenderScale.scaledDimensions(
                                        width, height, renderScale));
    }
}
