package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.FrameBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Small display application proving one graphics preset reaches a completed renderer frame. */
final class GraphicsPresetRendererSmokeApplication extends SimpleApplication {
    private static final int REQUIRED_RENDERED_FRAMES = 2;

    private final GraphicsQualityPreset preset;
    private final GraphicsRuntimeRenderScaleState runtimeRenderScaleState;
    private final CompletableFuture<Snapshot> completion = new CompletableFuture<>();
    private Node scene;
    private boolean completionProcessorAttached;

    GraphicsPresetRendererSmokeApplication(GraphicsQualityPreset preset) {
        this(preset, new GraphicsRuntimeRenderScaleState(Objects.requireNonNull(preset, "preset")));
    }

    private GraphicsPresetRendererSmokeApplication(
            GraphicsQualityPreset preset, GraphicsRuntimeRenderScaleState runtimeRenderScaleState) {
        super(runtimeRenderScaleState);
        this.preset = preset;
        this.runtimeRenderScaleState = runtimeRenderScaleState;
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        flyCam.setEnabled(false);

        scene = GraphicsBenchmarkReferenceScene.build(assetManager, preset);
        scene.setCullHint(Spatial.CullHint.Never);
        rootNode.attachChild(scene);

        cam.setLocation(new Vector3f(0.0f, 18.0f, 42.0f));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        if (!completionProcessorAttached && runtimeRenderScaleState.isInitialized()) {
            viewPort.addProcessor(new CompletionProcessor(scene));
            completionProcessorAttached = true;
        }
    }

    Snapshot awaitCompletion(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("renderer smoke timeout must be positive");
        }
        return completion.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private final class CompletionProcessor implements SceneProcessor {
        private final Node renderedScene;
        private boolean initialized;
        private int renderedFrames;

        private CompletionProcessor(Node renderedScene) {
            this.renderedScene = Objects.requireNonNull(renderedScene, "renderedScene");
        }

        @Override
        public void initialize(RenderManager renderManager, ViewPort viewPort) {
            Objects.requireNonNull(renderManager, "renderManager");
            Objects.requireNonNull(viewPort, "viewPort");
            if (initialized) {
                throw new IllegalStateException("renderer smoke processor is already initialized");
            }
            initialized = true;
        }

        @Override
        public void reshape(ViewPort viewPort, int width, int height) {
            if (!initialized) {
                throw new IllegalStateException("renderer smoke processor is not initialized");
            }
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("renderer smoke dimensions must be positive");
            }
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }

        @Override
        public void preFrame(float timePerFrame) {}

        @Override
        public void postQueue(RenderQueue renderQueue) {}

        @Override
        public void postFrame(FrameBuffer output) {
            if (completion.isDone()) {
                return;
            }
            renderedFrames++;
            if (renderedFrames < REQUIRED_RENDERED_FRAMES) {
                return;
            }
            completion.complete(
                    new Snapshot(
                            preset,
                            runtimeRenderScaleState.renderScale(),
                            runtimeRenderScaleState.offscreenProcessorAttached(),
                            countGeometries(renderedScene),
                            renderedFrames));
        }

        @Override
        public void cleanup() {
            initialized = false;
        }

        @Override
        public void setProfiler(AppProfiler profiler) {}
    }

    private static int countGeometries(Spatial spatial) {
        if (spatial instanceof Geometry) {
            return 1;
        }
        if (!(spatial instanceof Node node)) {
            return 0;
        }
        int count = 0;
        for (Spatial child : node.getChildren()) {
            count = Math.addExact(count, countGeometries(child));
        }
        return count;
    }

    record Snapshot(
            GraphicsQualityPreset preset,
            double renderScale,
            boolean offscreenProcessorAttached,
            int geometryCount,
            int renderedFrames) {
        Snapshot {
            Objects.requireNonNull(preset, "preset");
        }
    }
}
