package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.time.Duration;
import java.util.Objects;

/** Bounded timing policy for one challenge-proof-result exchange. */
public record IdentityExchangeConfig(
        Duration stepTimeout, Duration overallTimeout, Duration closeTimeout) {
    private static final Duration MAXIMUM_STEP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAXIMUM_OVERALL_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration MAXIMUM_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    public static final IdentityExchangeConfig DEFAULT =
            new IdentityExchangeConfig(
                    Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(3));

    public IdentityExchangeConfig {
        stepTimeout = requireDuration(stepTimeout, "stepTimeout", MAXIMUM_STEP_TIMEOUT);
        overallTimeout =
                requireDuration(overallTimeout, "overallTimeout", MAXIMUM_OVERALL_TIMEOUT);
        closeTimeout = requireDuration(closeTimeout, "closeTimeout", MAXIMUM_CLOSE_TIMEOUT);
        if (overallTimeout.compareTo(stepTimeout) < 0) {
            throw new IllegalArgumentException("overallTimeout cannot be shorter than stepTimeout");
        }
    }

    private static Duration requireDuration(
            Duration value, String field, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        if (duration.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " must be at least 1 millisecond");
        }
        return duration;
    }
}
