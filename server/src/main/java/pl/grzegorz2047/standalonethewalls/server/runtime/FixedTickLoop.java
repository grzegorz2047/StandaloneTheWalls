package pl.grzegorz2047.standalonethewalls.server.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded fixed-step scheduler with bounded catch-up.
 *
 * <p>It never performs network or disk I/O and does not own a thread. The runtime adapter decides
 * where it runs.
 */
public final class FixedTickLoop {
    public static final int DEFAULT_MAXIMUM_CATCH_UP_TICKS = 5;

    private final long tickPeriodNanos;
    private final int maximumCatchUpTicks;
    private final NanoClock clock;
    private final NanoSleeper sleeper;
    private final TickOverrunListener overrunListener;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    public FixedTickLoop(
            int tickRate,
            int maximumCatchUpTicks,
            NanoClock clock,
            NanoSleeper sleeper,
            TickOverrunListener overrunListener) {
        if (tickRate < 1 || tickRate > 1_000) {
            throw new IllegalArgumentException("tickRate must be between 1 and 1000");
        }
        if (maximumCatchUpTicks < 1 || maximumCatchUpTicks > 100) {
            throw new IllegalArgumentException("maximumCatchUpTicks must be between 1 and 100");
        }
        this.tickPeriodNanos = 1_000_000_000L / tickRate;
        this.maximumCatchUpTicks = maximumCatchUpTicks;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.overrunListener = Objects.requireNonNull(overrunListener, "overrunListener");
    }

    public void run(TickHandler handler) throws InterruptedException {
        Objects.requireNonNull(handler, "handler");
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("fixed tick loop is already running");
        }
        stopRequested.set(false);
        long tickNumber = 0L;
        long nextDeadline = clock.nanoTime();
        try {
            while (!stopRequested.get()) {
                long now = clock.nanoTime();
                if (now < nextDeadline) {
                    sleeper.sleepNanos(nextDeadline - now);
                    continue;
                }

                int catchUpTicks = 0;
                do {
                    handler.onTick(tickNumber++);
                    catchUpTicks++;
                    nextDeadline = Math.addExact(nextDeadline, tickPeriodNanos);
                    now = clock.nanoTime();
                } while (!stopRequested.get()
                        && now >= nextDeadline
                        && catchUpTicks < maximumCatchUpTicks);

                if (!stopRequested.get()
                        && catchUpTicks == maximumCatchUpTicks
                        && now >= nextDeadline) {
                    long skippedTicks = ((now - nextDeadline) / tickPeriodNanos) + 1L;
                    nextDeadline = Math.addExact(
                            nextDeadline, Math.multiplyExact(skippedTicks, tickPeriodNanos));
                    overrunListener.onSkippedTicks(skippedTicks);
                }
            }
        } finally {
            running.set(false);
        }
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long tickPeriodNanos() {
        return tickPeriodNanos;
    }
}
