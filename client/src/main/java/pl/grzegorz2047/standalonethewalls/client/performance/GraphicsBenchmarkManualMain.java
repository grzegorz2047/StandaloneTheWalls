package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Standalone no-network entrypoint for reproducible manual graphics benchmarks. */
public final class GraphicsBenchmarkManualMain {
    public static final int EXIT_OK = 0;
    public static final int EXIT_BENCHMARK_FAILED = 1;
    public static final int EXIT_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphicsBenchmarkManualMain.class);
    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(5);

    private GraphicsBenchmarkManualMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        int exitCode = run(arguments);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        GraphicsBenchmarkManualApplication application = null;
        try {
            PreparedRun prepared = prepare(arguments, BuildInfo.repositoryCommit());
            GraphicsBenchmarkReportStore reportStore =
                    new GraphicsBenchmarkReportStore(prepared.options().outputDirectory());
            application = new GraphicsBenchmarkManualApplication(prepared.config(), reportStore);
            configure(application, prepared.options());
            application.start(JmeContext.Type.Display, true);
            GraphicsBenchmarkSession.Outcome outcome =
                    application.awaitCompletion(COMPLETION_TIMEOUT);
            LOGGER.info(
                    "Graphics benchmark completed with {} and report {}.",
                    outcome.report().result().targetStatus(),
                    reportStore.reportFile());
            return EXIT_OK;
        } catch (IllegalArgumentException exception) {
            LOGGER.error(
                    "Usage: sunderfront-graphics-benchmark --preset <low|medium|high> --width <pixels> --height <pixels> --render-scale <decimal> --warm-up-frames <count> --measurement-frames <count> --asset-lock <path> --output-dir <directory>");
            return EXIT_USAGE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Graphics benchmark was interrupted.");
            return EXIT_BENCHMARK_FAILED;
        } catch (IOException | ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.error("Graphics benchmark failed.", exception);
            return EXIT_BENCHMARK_FAILED;
        } finally {
            if (application != null && application.getContext() != null) {
                application.stop(true);
            }
        }
    }

    static PreparedRun prepare(String[] arguments, Optional<String> repositoryCommit)
            throws IOException {
        GraphicsBenchmarkManualOptions options = GraphicsBenchmarkManualOptions.parse(arguments);
        String commit =
                Objects.requireNonNull(repositoryCommit, "repositoryCommit")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "build repository commit is unavailable"));
        GraphicsBenchmarkAssetIdentity assetIdentity =
                GraphicsBenchmarkAssetIdentity.fromLock(options.assetLock());
        GraphicsBenchmarkSession.Config config =
                new GraphicsBenchmarkSession.Config(
                        commit,
                        assetIdentity.compatibilityKey(),
                        options.preset(),
                        options.width(),
                        options.height(),
                        options.renderScale(),
                        options.warmUpFrames(),
                        options.measurementFrames());
        return new PreparedRun(options, config);
    }

    private static void configure(
            GraphicsBenchmarkManualApplication application,
            GraphicsBenchmarkManualOptions options) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(BuildInfo.PRODUCT_NAME + " Graphics Benchmark");
        settings.setResolution(options.width(), options.height());
        settings.setFullscreen(false);
        settings.setVSync(false);
        settings.setResizable(false);
        application.setSettings(settings);
        application.setShowSettings(false);
        application.setPauseOnLostFocus(false);
    }

    record PreparedRun(
            GraphicsBenchmarkManualOptions options, GraphicsBenchmarkSession.Config config) {
        PreparedRun {
            Objects.requireNonNull(options, "options");
            Objects.requireNonNull(config, "config");
        }
    }
}
