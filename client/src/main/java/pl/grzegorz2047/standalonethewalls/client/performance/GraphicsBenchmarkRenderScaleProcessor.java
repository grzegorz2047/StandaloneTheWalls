package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.asset.AssetManager;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import java.util.Objects;

/** Main-viewport framebuffer scaler shared by benchmark and runtime quality startup. */
final class GraphicsBenchmarkRenderScaleProcessor implements SceneProcessor {
    private final AssetManager assetManager;
    private final double renderScale;
    private final Camera upscaleCamera = new Camera(1, 1);

    private RenderManager renderManager;
    private Renderer renderer;
    private ViewPort viewPort;
    private Camera sceneCamera;
    private FrameBuffer originalOutput;
    private FrameBuffer scaledFrameBuffer;
    private Texture2D scaledColorTexture;
    private Picture upscaleQuad;
    private int displayWidth;
    private int displayHeight;

    GraphicsBenchmarkRenderScaleProcessor(AssetManager assetManager, double renderScale) {
        this.assetManager = Objects.requireNonNull(assetManager, "assetManager");
        if (!GraphicsBenchmarkRenderScale.requiresOffscreenRendering(renderScale)) {
            throw new IllegalArgumentException("render-scale processor requires a scale below 1.0");
        }
        this.renderScale = renderScale;
    }

    @Override
    public void initialize(RenderManager renderManager, ViewPort viewPort) {
        if (isInitialized()) {
            throw new IllegalStateException("render-scale processor is already initialized");
        }
        this.renderManager = Objects.requireNonNull(renderManager, "renderManager");
        this.renderer = Objects.requireNonNull(renderManager.getRenderer(), "renderer");
        this.viewPort = Objects.requireNonNull(viewPort, "viewPort");
        this.sceneCamera = Objects.requireNonNull(viewPort.getCamera(), "scene camera");
        this.originalOutput = viewPort.getOutputFrameBuffer();
        this.upscaleQuad = new Picture("BenchmarkRenderScaleUpscale", false);
        rebuild(sceneCamera.getWidth(), sceneCamera.getHeight());
    }

    @Override
    public void reshape(ViewPort viewPort, int width, int height) {
        requireViewPort(viewPort);
        rebuild(width, height);
    }

    @Override
    public boolean isInitialized() {
        return viewPort != null;
    }

    @Override
    public void preFrame(float timePerFrame) {
        requireInitialized();
        sceneCamera.resize(scaledFrameBuffer.getWidth(), scaledFrameBuffer.getHeight(), true);
        viewPort.setOutputFrameBuffer(scaledFrameBuffer);
    }

    @Override
    public void postQueue(RenderQueue renderQueue) {}

    @Override
    public void postFrame(FrameBuffer output) {
        requireInitialized();
        if (output != scaledFrameBuffer) {
            throw new IllegalStateException(
                    "benchmark scene was not rendered to the scaled framebuffer");
        }

        upscaleQuad.setWidth(displayWidth);
        upscaleQuad.setHeight(displayHeight);
        upscaleQuad.updateGeometricState();
        upscaleCamera.resize(displayWidth, displayHeight, true);

        renderer.setFrameBuffer(originalOutput);
        renderer.clearBuffers(true, true, false);
        renderManager.setCamera(upscaleCamera, true);
        renderManager.renderGeometry(upscaleQuad);
        renderManager.setCamera(sceneCamera, false);
    }

    @Override
    public void cleanup() {
        if (!isInitialized()) {
            return;
        }
        viewPort.setOutputFrameBuffer(originalOutput);
        sceneCamera.resize(displayWidth, displayHeight, true);
        disposeScaledResources();
        renderManager = null;
        renderer = null;
        viewPort = null;
        sceneCamera = null;
        originalOutput = null;
        upscaleQuad = null;
        displayWidth = 0;
        displayHeight = 0;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {}

    private void rebuild(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("display dimensions must be positive");
        }
        disposeScaledResources();

        GraphicsBenchmarkRenderScale.Dimensions scaled =
                GraphicsBenchmarkRenderScale.scaledDimensions(width, height, renderScale);
        Texture2D color = new Texture2D(scaled.width(), scaled.height(), Image.Format.RGBA8);
        color.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        color.setMagFilter(Texture.MagFilter.Bilinear);
        FrameBuffer frameBuffer = new FrameBuffer(scaled.width(), scaled.height(), 1);
        frameBuffer.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
        frameBuffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(color));

        displayWidth = width;
        displayHeight = height;
        scaledColorTexture = color;
        scaledFrameBuffer = frameBuffer;
        upscaleQuad.setTexture(assetManager, color, false);
        upscaleQuad.setPosition(0f, 0f);
        upscaleQuad.setWidth(width);
        upscaleQuad.setHeight(height);
        upscaleQuad.updateGeometricState();
        upscaleCamera.resize(width, height, true);
        sceneCamera.resize(scaled.width(), scaled.height(), true);
        viewPort.setOutputFrameBuffer(frameBuffer);
    }

    private void disposeScaledResources() {
        if (scaledFrameBuffer != null) {
            scaledFrameBuffer.dispose();
            scaledFrameBuffer = null;
        }
        if (scaledColorTexture != null) {
            scaledColorTexture.getImage().dispose();
            scaledColorTexture = null;
        }
    }

    private void requireViewPort(ViewPort candidate) {
        requireInitialized();
        if (candidate != viewPort) {
            throw new IllegalArgumentException(
                    "render-scale processor cannot move between viewports");
        }
    }

    private void requireInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("render-scale processor is not initialized");
        }
    }
}
