package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import java.util.Objects;
import java.util.OptionalDouble;

/** Applies one startup preset and bounded one-way render-scale adaptation to the main viewport. */
public final class GraphicsRuntimeRenderScaleState extends BaseAppState {
    private final GraphicsRuntimeRenderScaleGovernor governor;
    private SimpleApplication application;
    private GraphicsBenchmarkRenderScaleProcessor processor;
    private double renderScale;

    public GraphicsRuntimeRenderScaleState(GraphicsQualityPreset preset) {
        this(new GraphicsRuntimeRenderScaleGovernor(Objects.requireNonNull(preset, "preset")));
    }

    GraphicsRuntimeRenderScaleState(GraphicsRuntimeRenderScaleGovernor governor) {
        this.governor = Objects.requireNonNull(governor, "governor");
        this.renderScale = governor.currentRenderScale();
    }

    @Override
    protected void initialize(Application application) {
        if (!(application instanceof SimpleApplication simpleApplication)) {
            throw new IllegalArgumentException("runtime render scale requires SimpleApplication");
        }
        if (this.application != null || processor != null) {
            throw new IllegalStateException("runtime render-scale state is already initialized");
        }
        this.application = simpleApplication;
        applyRenderScale(governor.currentRenderScale());
    }

    @Override
    public void update(float timePerFrame) {
        OptionalDouble changedScale =
                governor.acceptFrameTime(JmeGraphicsTelemetrySampler.toFrameTimeNanos(timePerFrame));
        changedScale.ifPresent(this::applyRenderScale);
    }

    @Override
    protected void cleanup(Application application) {
        removeProcessor();
        this.application = null;
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    double renderScale() {
        return renderScale;
    }

    boolean offscreenProcessorAttached() {
        return processor != null;
    }

    int pendingFrameTimeSamples() {
        return governor.pendingSampleCount();
    }

    private void applyRenderScale(double newRenderScale) {
        if (Double.compare(renderScale, newRenderScale) == 0 && processor != null) {
            return;
        }
        removeProcessor();
        renderScale = newRenderScale;
        if (!GraphicsBenchmarkRenderScale.requiresOffscreenRendering(newRenderScale)) {
            return;
        }
        if (application == null) {
            throw new IllegalStateException("runtime render-scale state is not initialized");
        }
        processor =
                new GraphicsBenchmarkRenderScaleProcessor(
                        application.getAssetManager(), newRenderScale);
        application.getViewPort().addProcessor(processor);
    }

    private void removeProcessor() {
        if (processor == null) {
            return;
        }
        if (application != null) {
            application.getViewPort().removeProcessor(processor);
        } else {
            processor.cleanup();
        }
        processor = null;
    }
}
