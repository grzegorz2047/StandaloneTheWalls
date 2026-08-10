package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import java.util.Objects;

/** Applies one startup quality preset's real render scale to the main scene viewport. */
public final class GraphicsRuntimeRenderScaleState extends BaseAppState {
    private final double renderScale;
    private GraphicsBenchmarkRenderScaleProcessor processor;

    public GraphicsRuntimeRenderScaleState(GraphicsQualityPreset preset) {
        this.renderScale = Objects.requireNonNull(preset, "preset").defaultRenderScale();
    }

    @Override
    protected void initialize(Application application) {
        if (!(application instanceof SimpleApplication simpleApplication)) {
            throw new IllegalArgumentException("runtime render scale requires SimpleApplication");
        }
        if (!GraphicsBenchmarkRenderScale.requiresOffscreenRendering(renderScale)) {
            return;
        }
        if (processor != null) {
            throw new IllegalStateException("runtime render-scale state is already initialized");
        }
        processor =
                new GraphicsBenchmarkRenderScaleProcessor(
                        simpleApplication.getAssetManager(), renderScale);
        simpleApplication.getViewPort().addProcessor(processor);
    }

    @Override
    protected void cleanup(Application application) {
        if (processor == null) {
            return;
        }
        if (application instanceof SimpleApplication simpleApplication) {
            simpleApplication.getViewPort().removeProcessor(processor);
        } else {
            processor.cleanup();
        }
        processor = null;
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
}
