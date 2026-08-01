package pl.grzegorz2047.standalonethewalls.server.runtime;

/** Receives bounded scheduling debt metrics without logging from the loop itself. */
@FunctionalInterface
public interface TickOverrunListener {
    void onSkippedTicks(long skippedTicks);

    static TickOverrunListener ignoring() {
        return skippedTicks -> {};
    }
}
