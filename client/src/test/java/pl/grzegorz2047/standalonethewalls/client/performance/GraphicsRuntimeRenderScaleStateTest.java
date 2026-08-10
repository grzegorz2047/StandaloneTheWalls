package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class GraphicsRuntimeRenderScaleStateTest {
    @Test
    void lowAttachesScaledFramebufferAndCleanupRestoresDirectRuntimePath()
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeRenderScaleState state =
                new GraphicsRuntimeRenderScaleState(GraphicsQualityPreset.LOW);
        InitialStateApplication application = new InitialStateApplication(state);

        assertThat(run(application, application.processorCount)).isOne();
        assertThat(state.renderScale()).isEqualTo(0.75d);
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    @Test
    void mediumAndHighDefaultsDoNotAttachUnnecessaryOffscreenProcessors()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertDirect(GraphicsQualityPreset.MEDIUM);
        assertDirect(GraphicsQualityPreset.HIGH);
    }

    @Test
    void mediumTransitionsFromDirectRenderToScaledFramebufferOnGovernorReduction()
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeRenderScaleState state = controlledState(GraphicsQualityPreset.MEDIUM);
        ControlledBadFrameApplication application = new ControlledBadFrameApplication(state);

        DynamicSnapshot snapshot = run(application, application.snapshot);

        assertThat(snapshot.renderScale()).isEqualTo(0.95d);
        assertThat(snapshot.processorCount()).isOne();
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    @Test
    void lowReplacesExistingScalerWithoutLeakingProcessors()
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeRenderScaleState state = controlledState(GraphicsQualityPreset.LOW);
        ControlledBadFrameApplication application = new ControlledBadFrameApplication(state);

        DynamicSnapshot snapshot = run(application, application.snapshot);

        assertThat(snapshot.renderScale()).isEqualTo(0.70d);
        assertThat(snapshot.processorCount()).isOne();
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    private static void assertDirect(GraphicsQualityPreset preset)
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeRenderScaleState state = new GraphicsRuntimeRenderScaleState(preset);
        InitialStateApplication application = new InitialStateApplication(state);

        assertThat(run(application, application.processorCount)).isZero();
        assertThat(state.renderScale()).isEqualTo(1.0d);
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    private static GraphicsRuntimeRenderScaleState controlledState(GraphicsQualityPreset preset) {
        return new GraphicsRuntimeRenderScaleState(
                new GraphicsRuntimeRenderScaleGovernor(preset, 1, 0.05d, 1));
    }

    private static <T> T run(SimpleApplication application, CompletableFuture<T> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        application.setSettings(settings);
        try {
            application.start(JmeContext.Type.Headless, true);
            return result.get(Duration.ofSeconds(5).toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }

    private static final class InitialStateApplication extends SimpleApplication {
        private final GraphicsRuntimeRenderScaleState state;
        private final CompletableFuture<Integer> processorCount = new CompletableFuture<>();

        private InitialStateApplication(GraphicsRuntimeRenderScaleState state) {
            super(state);
            this.state = state;
        }

        @Override
        public void simpleInitApp() {}

        @Override
        public void simpleUpdate(float timePerFrame) {
            if (state.isInitialized()) {
                processorCount.complete(getViewPort().getProcessors().size());
            }
        }
    }

    private static final class ControlledBadFrameApplication extends SimpleApplication {
        private final GraphicsRuntimeRenderScaleState state;
        private final CompletableFuture<DynamicSnapshot> snapshot = new CompletableFuture<>();

        private ControlledBadFrameApplication(GraphicsRuntimeRenderScaleState state) {
            super(state);
            this.state = state;
        }

        @Override
        public void simpleInitApp() {
            state.setEnabled(false);
        }

        @Override
        public void simpleUpdate(float timePerFrame) {
            if (!state.isInitialized() || snapshot.isDone()) {
                return;
            }
            state.update(0.050f);
            snapshot.complete(
                    new DynamicSnapshot(state.renderScale(), getViewPort().getProcessors().size()));
        }
    }

    private record DynamicSnapshot(double renderScale, int processorCount) {}
}
