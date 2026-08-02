package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.time.Duration;
import java.util.Objects;

/** Hard admission and shutdown limits for one asynchronous reliable channel. */
public record AsyncReliableChannelConfig(
        int maximumPendingSends, long maximumPendingSendBytes, Duration closeTimeout) {
    public static final AsyncReliableChannelConfig DEFAULT =
            new AsyncReliableChannelConfig(256, 1024L * 1024L, Duration.ofSeconds(5));

    private static final int MAXIMUM_SEND_OPERATIONS = 4096;
    private static final long MAXIMUM_SEND_BYTES = 64L * 1024L * 1024L;
    private static final Duration MAXIMUM_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    public AsyncReliableChannelConfig {
        if (maximumPendingSends < 1 || maximumPendingSends > MAXIMUM_SEND_OPERATIONS) {
            throw new IllegalArgumentException("maximum pending sends must be between 1 and 4096");
        }
        if (maximumPendingSendBytes < 1L || maximumPendingSendBytes > MAXIMUM_SEND_BYTES) {
            throw new IllegalArgumentException(
                    "maximum pending send bytes must be between 1 and 67108864");
        }
        closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
        if (closeTimeout.isZero()
                || closeTimeout.isNegative()
                || closeTimeout.compareTo(MAXIMUM_CLOSE_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "close timeout must be positive and no longer than 30 seconds");
        }
    }
}
