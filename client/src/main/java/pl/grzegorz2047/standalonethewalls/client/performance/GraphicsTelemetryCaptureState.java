package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import java.lang.management.ManagementFactory;
import java.util.Optional;

/** Captures a bounded local telemetry window from the running display client. */
public final class GraphicsTelemetryCaptureState extends BaseAppState {
    private final GraphicsTelemetryWindow window = new GraphicsTelemetryWindow();
    private SimpleApplication application;
    private JmeGraphicsTelemetrySampler sampler;

    @Override
    protected void initialize(Application application) {
        if (!(application instanceof SimpleApplication simpleApplication)) {
            throw new IllegalArgumentException("graphics telemetry requires SimpleApplication");
        }
        this.application = simpleApplication;
        sampler =
                JmeGraphicsTelemetrySampler.forRenderer(
                        application.getRenderer(), GraphicsTelemetryCaptureState::usedJvmHeapBytes);
    }

    @Override
    public void update(float timePerFrame) {
        if (sampler == null || application == null) {
            return;
        }
        sampler
                .sample(timePerFrame, application.getRootNode(), application.getGuiNode())
                .ifPresent(window::add);
    }

    @Override
    protected void cleanup(Application application) {
        if (sampler != null) {
            sampler.close();
            sampler = null;
        }
        this.application = null;
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    public int sampleCount() {
        return window.sampleCount();
    }

    public Optional<GraphicsTelemetrySummary> summary() {
        return window.summary();
    }

    private static long usedJvmHeapBytes() {
        return Math.max(0L, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }
}
