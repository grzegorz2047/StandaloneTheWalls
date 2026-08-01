package pl.grzegorz2047.standalonethewalls.server.runtime;

/** Interruptible scheduler wait boundary. */
@FunctionalInterface
public interface NanoSleeper {
    void sleepNanos(long nanoseconds) throws InterruptedException;
}
