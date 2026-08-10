package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.SimpleApplication;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** No-network jME application that runs one benchmark and persists its durable report. */
final class GraphicsBenchmarkManualApplication extends SimpleApplication {
    private final GraphicsBenchmarkRunState benchmarkState;
    private final GraphicsBenchmarkReportStore reportStore;
    private final double renderScale;
    private final CompletableFuture<GraphicsBenchmarkSession.Outcome> completion =
            new CompletableFuture<>();

    GraphicsBenchmarkManualApplication(
            GraphicsBenchmarkSession.Config config, GraphicsBenchmarkReportStore reportStore) {
        this(
                new GraphicsBenchmarkRunState(config, java.util.Optional.empty()),
                reportStore,
                config.renderScale());
    }

    GraphicsBenchmarkManualApplication(
            GraphicsBenchmarkRunState benchmarkState, GraphicsBenchmarkReportStore reportStore) {
        this(benchmarkState, reportStore, GraphicsBenchmarkRenderScale.DIRECT_RENDER_SCALE);
    }

    GraphicsBenchmarkManualApplication(
            GraphicsBenchmarkRunState benchmarkState,
            GraphicsBenchmarkReportStore reportStore,
            double renderScale) {
        super();
        this.benchmarkState = Objects.requireNonNull(benchmarkState, "benchmarkState");
        this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
        GraphicsBenchmarkRenderScale.requireScale(renderScale);
        this.renderScale = renderScale;
    }

    @Override
    public void simpleInitApp() {
        if (!getStateManager().attach(benchmarkState)) {
            throw new IllegalStateException("graphics benchmark state could not be attached");
        }
        if (GraphicsBenchmarkRenderScale.requiresOffscreenRendering(renderScale)) {
            viewPort.addProcessor(
                    new GraphicsBenchmarkRenderScaleProcessor(assetManager, renderScale));
        }
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        if (completion.isDone()) {
            return;
        }
        benchmarkState
                .outcome()
                .ifPresent(
                        outcome -> {
                            try {
                                reportStore.save(outcome.report());
                                if (!completion.complete(outcome)) {
                                    throw new IllegalStateException(
                                            "graphics benchmark completion changed concurrently");
                                }
                            } catch (IOException exception) {
                                if (!completion.completeExceptionally(exception)) {
                                    throw new IllegalStateException(
                                            "graphics benchmark failure changed concurrently",
                                            exception);
                                }
                            }
                        });
    }

    GraphicsBenchmarkSession.Outcome awaitCompletion(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(timeout, "timeout");
        long timeoutNanos;
        try {
            timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "benchmark timeout is outside the bounded range", exception);
        }
        if (timeoutNanos <= 0L) {
            throw new IllegalArgumentException("benchmark timeout must be positive");
        }
        return completion.get(timeoutNanos, TimeUnit.NANOSECONDS);
    }
}
