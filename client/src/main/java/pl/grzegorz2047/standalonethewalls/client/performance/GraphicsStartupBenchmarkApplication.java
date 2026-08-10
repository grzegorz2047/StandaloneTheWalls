package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.SimpleApplication;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** No-network display application that returns one benchmark outcome without persisting it. */
final class GraphicsStartupBenchmarkApplication extends SimpleApplication {
    private final GraphicsBenchmarkRunState benchmarkState;
    private final CompletableFuture<GraphicsBenchmarkSession.Outcome> completion =
            new CompletableFuture<>();

    GraphicsStartupBenchmarkApplication(
            GraphicsBenchmarkSession.Config config, Optional<GraphicsQualityState> previousState) {
        this(new GraphicsBenchmarkRunState(config, previousState));
    }

    GraphicsStartupBenchmarkApplication(GraphicsBenchmarkRunState benchmarkState) {
        super();
        this.benchmarkState = Objects.requireNonNull(benchmarkState, "benchmarkState");
    }

    @Override
    public void simpleInitApp() {
        try {
            if (!getStateManager().attach(benchmarkState)) {
                throw new IllegalStateException("startup graphics benchmark state could not be attached");
            }
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
            stop(false);
        }
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        if (completion.isDone()) {
            return;
        }
        benchmarkState.outcome().ifPresent(completion::complete);
    }

    GraphicsBenchmarkSession.Outcome awaitCompletion(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(timeout, "timeout");
        long timeoutNanos;
        try {
            timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "startup benchmark timeout is outside the bounded range", exception);
        }
        if (timeoutNanos <= 0L) {
            throw new IllegalArgumentException("startup benchmark timeout must be positive");
        }
        return completion.get(timeoutNanos, TimeUnit.NANOSECONDS);
    }
}
