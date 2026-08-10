package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Executes the fixed no-network startup benchmark in one temporary display application. */
final class GraphicsStartupBenchmarkRunner {
    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(90);

    private GraphicsStartupBenchmarkRunner() {
        throw new AssertionError("No instances");
    }

    static GraphicsBenchmarkSession.Outcome run(
            GraphicsBenchmarkSession.Config config, Optional<GraphicsQualityState> previousState)
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsStartupBenchmarkApplication application =
                new GraphicsStartupBenchmarkApplication(config, previousState);
        configure(application);
        try {
            application.start(JmeContext.Type.Display, true);
            return application.awaitCompletion(COMPLETION_TIMEOUT);
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }

    private static void configure(GraphicsStartupBenchmarkApplication application) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(BuildInfo.PRODUCT_NAME + " First-Run Graphics Benchmark");
        settings.setResolution(
                GraphicsStartupBenchmarkProfile.WIDTH, GraphicsStartupBenchmarkProfile.HEIGHT);
        settings.setFullscreen(false);
        settings.setVSync(false);
        settings.setResizable(false);
        settings.setAudioRenderer(null);
        application.setSettings(settings);
        application.setShowSettings(false);
        application.setPauseOnLostFocus(false);
    }
}
