package pl.grzegorz2047.standalonethewalls.server.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns exactly one simulation thread and provides deterministic shutdown semantics. */
public final class ServerRuntime implements AutoCloseable {
    private final FixedTickLoop loop;
    private final TickHandler handler;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private Thread simulationThread;

    public ServerRuntime(FixedTickLoop loop, TickHandler handler) {
        this.loop = Objects.requireNonNull(loop, "loop");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public synchronized void start() {
        if (simulationThread != null) {
            throw new IllegalStateException("server runtime can be started only once");
        }
        simulationThread = Thread.ofPlatform()
                .name("sunderfront-simulation")
                .daemon(false)
                .unstarted(this::runLoop);
        simulationThread.start();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }
        Thread thread = requireStarted();
        if (timeout.isZero()) {
            return !thread.isAlive();
        }
        long millis = timeout.toMillis();
        int nanos = (int) timeout.minusMillis(millis).toNanos();
        thread.join(millis, nanos);
        return !thread.isAlive();
    }

    public void awaitTermination() throws InterruptedException {
        requireStarted().join();
    }

    public boolean isRunning() {
        Thread thread;
        synchronized (this) {
            thread = simulationThread;
        }
        return thread != null && thread.isAlive();
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure.get());
    }

    @Override
    public void close() {
        Thread thread;
        synchronized (this) {
            thread = simulationThread;
        }
        if (thread == null) {
            return;
        }
        loop.requestStop();
        thread.interrupt();
        if (thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(5_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while stopping server runtime", exception);
        }
        if (thread.isAlive()) {
            throw new IllegalStateException("simulation thread did not stop within 5 seconds");
        }
    }

    private void runLoop() {
        try {
            loop.run(handler);
        } catch (InterruptedException exception) {
            if (!loop.isStopRequested()) {
                failure.compareAndSet(null, exception);
                Thread.currentThread().interrupt();
            }
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private synchronized Thread requireStarted() {
        if (simulationThread == null) {
            throw new IllegalStateException("server runtime has not been started");
        }
        return simulationThread;
    }
}
