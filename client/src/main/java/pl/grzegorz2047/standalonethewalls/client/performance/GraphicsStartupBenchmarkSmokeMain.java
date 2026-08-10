package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CI entrypoint proving a first-run benchmark persists a reusable local quality choice. */
public final class GraphicsStartupBenchmarkSmokeMain {
    static final int EXIT_OK = 0;
    static final int EXIT_BENCHMARK_FAILURE = 1;
    static final int EXIT_USAGE = 2;
    private static final Logger LOGGER =
            LoggerFactory.getLogger(GraphicsStartupBenchmarkSmokeMain.class);

    private GraphicsStartupBenchmarkSmokeMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        System.exit(run(arguments));
    }

    static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != 2) {
            LOGGER.error("Startup benchmark smoke requires asset-lock and data-directory paths.");
            return EXIT_USAGE;
        }
        Path assetLock;
        Path dataDirectory;
        try {
            assetLock = Path.of(arguments[0]).toAbsolutePath().normalize();
            dataDirectory = Path.of(arguments[1]).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            LOGGER.error("Startup benchmark smoke paths are invalid.", exception);
            return EXIT_USAGE;
        }

        try {
            GraphicsQualityPreset selected =
                    GraphicsAutomaticQualitySelection.resolve(dataDirectory, assetLock);
            Optional<GraphicsQualityPreset> reusable =
                    GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                            dataDirectory, assetLock);
            if (reusable.isEmpty() || reusable.orElseThrow() != selected) {
                throw new IllegalStateException(
                        "automatic startup benchmark did not persist a reusable preset");
            }
            if (new GraphicsQualityStateStore(dataDirectory).load().isEmpty()) {
                throw new IllegalStateException(
                        "automatic startup benchmark did not persist quality state");
            }
            if (new GraphicsBenchmarkReportStore(dataDirectory).load().isEmpty()) {
                throw new IllegalStateException(
                        "automatic startup benchmark did not persist its report");
            }
            LOGGER.info("Automatic startup graphics benchmark smoke passed with {}.", selected);
            return EXIT_OK;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Automatic startup graphics benchmark smoke was interrupted.");
            return EXIT_BENCHMARK_FAILURE;
        } catch (IOException | ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.error("Automatic startup graphics benchmark smoke failed.", exception);
            return EXIT_BENCHMARK_FAILURE;
        }
    }
}
