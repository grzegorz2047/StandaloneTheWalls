package pl.grzegorz2047.standalonethewalls.server.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ServerRuntimeTest {
    @Test
    void terminatesItsOnlyThreadAfterARequestedTickBudget() throws Exception {
        AdvancingClock clock = new AdvancingClock();
        FixedTickLoop loop = new FixedTickLoop(20, 5, clock, clock::advance, skipped -> {});
        AtomicLong ticks = new AtomicLong();
        ServerRuntime runtime = new ServerRuntime(loop, tick -> {
            if (ticks.incrementAndGet() == 5L) {
                loop.requestStop();
            }
        });

        runtime.start();

        assertTrue(runtime.awaitTermination(Duration.ofSeconds(1)));
        assertFalse(runtime.isRunning());
        assertTrue(runtime.failure().isEmpty());
        runtime.close();
    }

    @Test
    void capturesHandlerFailureAndCannotBeStartedTwice() throws Exception {
        AdvancingClock clock = new AdvancingClock();
        FixedTickLoop loop = new FixedTickLoop(20, 5, clock, clock::advance, skipped -> {});
        ServerRuntime runtime = new ServerRuntime(loop, tick -> {
            throw new IllegalStateException("boom");
        });

        runtime.start();

        assertTrue(runtime.awaitTermination(Duration.ofSeconds(1)));
        assertTrue(runtime.failure().orElseThrow() instanceof IllegalStateException);
        assertThrows(IllegalStateException.class, runtime::start);
        runtime.close();
    }

    @Test
    void zeroTimeoutNeverBlocks() {
        AdvancingClock clock = new AdvancingClock();
        FixedTickLoop loop = new FixedTickLoop(20, 5, clock, nanos -> Thread.sleep(1_000L), skipped -> {});
        ServerRuntime runtime = new ServerRuntime(loop, tick -> {});
        runtime.start();
        try {
            assertFalse(runtime.awaitTermination(Duration.ZERO));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } finally {
            runtime.close();
        }
    }

    private static final class AdvancingClock implements NanoClock {
        private long now;

        @Override
        public synchronized long nanoTime() {
            return now;
        }

        synchronized void advance(long nanoseconds) {
            now = Math.addExact(now, nanoseconds);
        }
    }
}
