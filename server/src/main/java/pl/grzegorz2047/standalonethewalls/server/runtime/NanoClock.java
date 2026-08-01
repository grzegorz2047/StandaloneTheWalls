package pl.grzegorz2047.standalonethewalls.server.runtime;

/** Monotonic time source used only for scheduling simulation ticks. */
@FunctionalInterface
public interface NanoClock {
    long nanoTime();
}
