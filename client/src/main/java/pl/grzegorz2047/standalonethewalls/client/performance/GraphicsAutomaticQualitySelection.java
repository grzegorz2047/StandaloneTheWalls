package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Resolves one startup quality preset, running and persisting a benchmark only when required. */
public final class GraphicsAutomaticQualitySelection {
    private GraphicsAutomaticQualitySelection() {
        throw new AssertionError("No instances");
    }

    public static GraphicsQualityPreset resolve(Path dataDirectory, Path assetLock)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return resolve(
                dataDirectory,
                assetLock,
                BuildInfo.repositoryCommit(),
                GraphicsStartupBenchmarkRunner::run);
    }

    static GraphicsQualityPreset resolve(
            Path dataDirectory,
            Path assetLock,
            Optional<String> repositoryCommit,
            BenchmarkRunner benchmarkRunner)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(assetLock, "assetLock");
        Objects.requireNonNull(repositoryCommit, "repositoryCommit");
        Objects.requireNonNull(benchmarkRunner, "benchmarkRunner");

        GraphicsBenchmarkCompatibilityKey currentKey =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock).compatibilityKey();
        GraphicsQualityStartupCoordinator coordinator =
                new GraphicsQualityStartupCoordinator(dataDirectory, currentKey);
        GraphicsQualityStartupCoordinator.StartupPlan plan = coordinator.begin();
        if (plan.action() == GraphicsQualityStartupDecision.Action.USE_PERSISTED_PRESET) {
            return plan.effectivePreset().orElseThrow();
        }

        String commit =
                repositoryCommit.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "build repository commit is unavailable for automatic graphics benchmark"));
        GraphicsBenchmarkSession.Config config =
                GraphicsStartupBenchmarkProfile.config(commit, currentKey);
        GraphicsBenchmarkSession.Outcome outcome =
                benchmarkRunner.run(config, plan.benchmarkPreviousState());
        return coordinator.completeBenchmark(outcome);
    }

    @FunctionalInterface
    interface BenchmarkRunner {
        GraphicsBenchmarkSession.Outcome run(
                GraphicsBenchmarkSession.Config config,
                Optional<GraphicsQualityState> previousState)
                throws InterruptedException, ExecutionException, TimeoutException;
    }
}
