package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.Renderer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Runs one opt-in reference benchmark through the real jME frame loop. */
public final class GraphicsBenchmarkRunState extends BaseAppState {
    private final GraphicsBenchmarkSession session;
    private final GraphicsQualityPreset measuredPreset;
    private final SceneFactory sceneFactory;
    private final TelemetrySourceFactory telemetrySourceFactory;
    private Node applicationRoot;
    private Node benchmarkScene;
    private TelemetrySource telemetrySource;
    private JmeGpuFrameTimeProfiler gpuFrameTimeProfiler;
    private GraphicsBenchmarkSession.Outcome completedOutcome;

    public GraphicsBenchmarkRunState(
            GraphicsBenchmarkSession.Config config, Optional<GraphicsQualityState> previousState) {
        this(
                config,
                previousState,
                GraphicsBenchmarkReferenceScene::build,
                GraphicsBenchmarkRunState::createTelemetrySource);
    }

    GraphicsBenchmarkRunState(
            GraphicsBenchmarkSession.Config config,
            Optional<GraphicsQualityState> previousState,
            SceneFactory sceneFactory,
            TelemetrySourceFactory telemetrySourceFactory) {
        GraphicsBenchmarkSession.Config checkedConfig = Objects.requireNonNull(config, "config");
        session = new GraphicsBenchmarkSession(checkedConfig, previousState);
        measuredPreset = checkedConfig.measuredPreset();
        this.sceneFactory = Objects.requireNonNull(sceneFactory, "sceneFactory");
        this.telemetrySourceFactory =
                Objects.requireNonNull(telemetrySourceFactory, "telemetrySourceFactory");
    }

    @Override
    protected void initialize(Application application) {
        if (!(application instanceof SimpleApplication simpleApplication)) {
            throw new IllegalArgumentException("graphics benchmark requires SimpleApplication");
        }
        if (benchmarkScene != null || telemetrySource != null || gpuFrameTimeProfiler != null) {
            throw new IllegalStateException("graphics benchmark state is already initialized");
        }

        Renderer renderer = application.getRenderer();
        JmeGpuFrameTimeProfiler newGpuProfiler = null;
        TelemetrySource newTelemetrySource = null;
        boolean initialized = false;
        try {
            GpuFrameTimeSource gpuFrameTimeSource = OptionalLong::empty;
            if (renderer != null && application.getAppProfiler() == null) {
                JmeGpuFrameTimeProfiler candidate =
                        JmeGpuFrameTimeProfiler.create(renderer, session::phase);
                if (candidate.enabled()) {
                    application.setAppProfiler(candidate);
                    newGpuProfiler = candidate;
                    gpuFrameTimeSource = candidate;
                } else {
                    candidate.close();
                }
            }

            newTelemetrySource =
                    Objects.requireNonNull(
                            telemetrySourceFactory.create(renderer, gpuFrameTimeSource),
                            "benchmark telemetry source");
            Node newScene = sceneFactory.build(application.getAssetManager(), measuredPreset);
            Objects.requireNonNull(newScene, "benchmark scene");
            if (newScene.getParent() != null) {
                throw new IllegalArgumentException("benchmark scene must be detached");
            }
            newScene.setCullHint(Spatial.CullHint.Never);
            simpleApplication.getRootNode().attachChild(newScene);

            applicationRoot = simpleApplication.getRootNode();
            benchmarkScene = newScene;
            telemetrySource = newTelemetrySource;
            gpuFrameTimeProfiler = newGpuProfiler;
            initialized = true;
        } finally {
            if (!initialized) {
                if (newTelemetrySource != null) {
                    newTelemetrySource.close();
                }
                uninstallGpuProfiler(application, newGpuProfiler);
            }
        }
    }

    @Override
    public void update(float timePerFrame) {
        if (telemetrySource == null || benchmarkScene == null || completedOutcome != null) {
            return;
        }
        telemetrySource
                .sample(timePerFrame, benchmarkScene)
                .flatMap(session::accept)
                .ifPresent(outcome -> completedOutcome = outcome);
    }

    @Override
    protected void cleanup(Application application) {
        if (telemetrySource != null) {
            telemetrySource.close();
            telemetrySource = null;
        }
        uninstallGpuProfiler(application, gpuFrameTimeProfiler);
        gpuFrameTimeProfiler = null;
        if (benchmarkScene != null) {
            benchmarkScene.removeFromParent();
            benchmarkScene = null;
        }
        applicationRoot = null;
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    public GraphicsBenchmarkSession.Phase phase() {
        return session.phase();
    }

    public int warmUpFramesRemaining() {
        return session.warmUpFramesRemaining();
    }

    public int measurementFramesCollected() {
        return session.measurementFramesCollected();
    }

    public int measurementFramesRemaining() {
        return session.measurementFramesRemaining();
    }

    public Optional<GraphicsBenchmarkSession.Outcome> outcome() {
        return Optional.ofNullable(completedOutcome);
    }

    boolean benchmarkSceneAttached() {
        return benchmarkScene != null
                && applicationRoot != null
                && benchmarkScene.getParent() == applicationRoot;
    }

    private static TelemetrySource createTelemetrySource(
            Renderer renderer, GpuFrameTimeSource gpuFrameTimeSource) {
        JmeGraphicsTelemetrySampler sampler =
                JmeGraphicsTelemetrySampler.forRenderer(
                        Objects.requireNonNull(renderer, "benchmark renderer"),
                        GraphicsBenchmarkRunState::usedJvmHeapBytes,
                        gpuFrameTimeSource);
        return new TelemetrySource() {
            @Override
            public Optional<GraphicsTelemetrySample> sample(
                    float timePerFrame, Node benchmarkScene) {
                return sampler.sample(timePerFrame, benchmarkScene);
            }

            @Override
            public void close() {
                sampler.close();
            }
        };
    }

    private static void uninstallGpuProfiler(
            Application application, JmeGpuFrameTimeProfiler gpuProfiler) {
        if (gpuProfiler == null) {
            return;
        }
        if (application.getAppProfiler() == gpuProfiler) {
            application.setAppProfiler(null);
        }
        gpuProfiler.close();
    }

    private static long usedJvmHeapBytes() {
        return Math.max(0L, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }

    @FunctionalInterface
    interface SceneFactory {
        Node build(AssetManager assetManager, GraphicsQualityPreset preset);
    }

    @FunctionalInterface
    interface TelemetrySourceFactory {
        TelemetrySource create(Renderer renderer, GpuFrameTimeSource gpuFrameTimeSource);
    }

    interface TelemetrySource {
        Optional<GraphicsTelemetrySample> sample(float timePerFrame, Node benchmarkScene);

        void close();
    }
}
