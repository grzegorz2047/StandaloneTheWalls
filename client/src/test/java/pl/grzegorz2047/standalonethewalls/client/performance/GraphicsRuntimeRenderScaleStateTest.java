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
        TestApplication application = new TestApplication(state);

        assertThat(run(application)).isOne();
        assertThat(state.renderScale()).isEqualTo(0.75d);
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    @Test
    void mediumAndHighDefaultsDoNotAttachUnnecessaryOffscreenProcessors()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertDirect(GraphicsQualityPreset.MEDIUM);
        assertDirect(GraphicsQualityPreset.HIGH);
    }

    private static void assertDirect(GraphicsQualityPreset preset)
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeRenderScaleState state = new GraphicsRuntimeRenderScaleState(preset);
        TestApplication application = new TestApplication(state);

        assertThat(run(application)).isZero();
        assertThat(state.renderScale()).isEqualTo(1.0d);
        assertThat(state.offscreenProcessorAttached()).isFalse();
    }

    private static int run(TestApplication application)
            throws InterruptedException, ExecutionException, TimeoutException {
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        application.setSettings(settings);
        try {
            application.start(JmeContext.Type.Headless, true);
            return application.awaitProcessorCount(Duration.ofSeconds(5));
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }

    private static final class TestApplication extends SimpleApplication {
        private final GraphicsRuntimeRenderScaleState state;
        private final CompletableFuture<Integer> processorCount = new CompletableFuture<>();

        private TestApplication(GraphicsRuntimeRenderScaleState state) {
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

        private int awaitProcessorCount(Duration timeout)
                throws InterruptedException, ExecutionException, TimeoutException {
            return processorCount.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }
}
