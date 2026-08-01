package pl.grzegorz2047.standalonethewalls.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedTickLoopTest {
    @Test
    void schedulesDeterministicTicksWithoutUsingWallClockTime() throws Exception {
        MutableNanoClock clock = new MutableNanoClock(100L);
        FixedTickLoop loop = new FixedTickLoop(20, 5, clock, clock::advance, skipped -> {});
        List<Long> tickTimes = new ArrayList<>();

        loop.run(tick -> {
            tickTimes.add(clock.nanoTime());
            if (tick == 3L) {
                loop.requestStop();
            }
        });

        assertEquals(List.of(100L, 50_000_100L, 100_000_100L, 150_000_100L), tickTimes);
        assertFalse(loop.isRunning());
    }

    @Test
    void boundsCatchUpAndReportsDroppedSchedulingDebt() throws Exception {
        MutableNanoClock clock = new MutableNanoClock(0L);
        AtomicLong skipped = new AtomicLong();
        FixedTickLoop loop = new FixedTickLoop(20, 3, clock, clock::advance, skipped::addAndGet);
        AtomicLong executed = new AtomicLong();

        loop.run(tick -> {
            long count = executed.incrementAndGet();
            if (count == 1L) {
                clock.advance(500_000_000L);
            }
            if (count == 4L) {
                loop.requestStop();
            }
        });

        assertEquals(4L, executed.get());
        assertEquals(8L, skipped.get());
    }

    @Test
    void rejectsConcurrentRunAndUnsafeConfiguration() throws Exception {
        MutableNanoClock clock = new MutableNanoClock(0L);
        FixedTickLoop[] holder = new FixedTickLoop[1];
        holder[0] = new FixedTickLoop(20, 5, clock, nanos -> {
            assertThrows(
                    IllegalStateException.class,
                    () -> holder[0].run(tick -> {}));
            holder[0].requestStop();
        }, skipped -> {});
        FixedTickLoop loop = holder[0];

        loop.run(tick -> {});

        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedTickLoop(0, 5, clock, clock::advance, skipped -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedTickLoop(20, 0, clock, clock::advance, skipped -> {}));
    }

    private static final class MutableNanoClock implements NanoClock {
        private long now;

        private MutableNanoClock(long now) {
            this.now = now;
        }

        @Override
        public long nanoTime() {
            return now;
        }

        void advance(long nanoseconds) {
            now = Math.addExact(now, nanoseconds);
        }
    }
}
