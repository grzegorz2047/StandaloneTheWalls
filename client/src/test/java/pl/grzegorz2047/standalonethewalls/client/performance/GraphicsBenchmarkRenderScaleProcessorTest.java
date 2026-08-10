package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.system.NullRenderer;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkRenderScaleProcessorTest {
    @Test
    void initializeReshapeAndCleanupPreserveDisplayContract() {
        NullRenderer renderer = new NullRenderer();
        renderer.initialize();
        RenderManager renderManager = new RenderManager(renderer);
        Camera camera = new Camera(1280, 720);
        ViewPort viewPort = new ViewPort("benchmark", camera);
        GraphicsBenchmarkRenderScaleProcessor processor =
                new GraphicsBenchmarkRenderScaleProcessor(new DesktopAssetManager(true), 0.75d);

        processor.initialize(renderManager, viewPort);

        assertThat(processor.isInitialized()).isTrue();
        assertThat(viewPort.getOutputFrameBuffer()).isNotNull();
        assertThat(viewPort.getOutputFrameBuffer().getWidth()).isEqualTo(960);
        assertThat(viewPort.getOutputFrameBuffer().getHeight()).isEqualTo(540);
        assertThat(camera.getWidth()).isEqualTo(960);
        assertThat(camera.getHeight()).isEqualTo(540);

        processor.reshape(viewPort, 1920, 1080);

        assertThat(viewPort.getOutputFrameBuffer().getWidth()).isEqualTo(1440);
        assertThat(viewPort.getOutputFrameBuffer().getHeight()).isEqualTo(810);
        assertThat(camera.getWidth()).isEqualTo(1440);
        assertThat(camera.getHeight()).isEqualTo(810);

        processor.cleanup();
        processor.cleanup();

        assertThat(processor.isInitialized()).isFalse();
        assertThat(viewPort.getOutputFrameBuffer()).isNull();
        assertThat(camera.getWidth()).isEqualTo(1920);
        assertThat(camera.getHeight()).isEqualTo(1080);
    }

    @Test
    void directRenderScaleDoesNotCreateAnOffscreenProcessor() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkRenderScaleProcessor(
                                        new DesktopAssetManager(true), 1.0d));
    }
}
