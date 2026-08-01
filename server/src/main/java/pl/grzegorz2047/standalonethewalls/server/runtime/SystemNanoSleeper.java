package pl.grzegorz2047.standalonethewalls.server.runtime;

import java.util.concurrent.locks.LockSupport;

/** Production sleeper using monotonic parking and interruption checks. */
public final class SystemNanoSleeper implements NanoSleeper {
    @Override
    public void sleepNanos(long nanoseconds) throws InterruptedException {
        if (nanoseconds <= 0L) {
            return;
        }
        LockSupport.parkNanos(nanoseconds);
        if (Thread.interrupted()) {
            throw new InterruptedException("simulation wait interrupted");
        }
    }
}
