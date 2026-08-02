package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.time.Duration;
import java.util.Objects;

/** Immutable bounded cadence and retry policy for automatic HTTPS registry refresh. */
public record RegistryRefreshScheduleConfiguration(
        boolean enabled,
        Duration initialDelay,
        Duration successInterval,
        Duration initialFailureBackoff,
        Duration maximumFailureBackoff,
        Duration maximumJitter) {
    public static final Duration MINIMUM_RETRY_DELAY = Duration.ofSeconds(1);
    public static final Duration MAXIMUM_DELAY = Duration.ofDays(7);
    public static final Duration MAXIMUM_JITTER = Duration.ofHours(1);
    public static final RegistryRefreshScheduleConfiguration DEFAULT =
            new RegistryRefreshScheduleConfiguration(
                    false,
                    Duration.ofMinutes(1),
                    Duration.ofHours(1),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(30),
                    Duration.ofSeconds(5));

    public RegistryRefreshScheduleConfiguration {
        initialDelay = requireDelay(initialDelay, "initialDelay", true, MAXIMUM_DELAY);
        successInterval = requireDelay(successInterval, "successInterval", false, MAXIMUM_DELAY);
        initialFailureBackoff =
                requireDelay(
                        initialFailureBackoff,
                        "initialFailureBackoff",
                        false,
                        MAXIMUM_DELAY);
        maximumFailureBackoff =
                requireDelay(
                        maximumFailureBackoff,
                        "maximumFailureBackoff",
                        false,
                        MAXIMUM_DELAY);
        maximumJitter = requireDelay(maximumJitter, "maximumJitter", true, MAXIMUM_JITTER);
        if (initialFailureBackoff.compareTo(MINIMUM_RETRY_DELAY) < 0) {
            throw new IllegalArgumentException(
                    "initialFailureBackoff is below the safe retry minimum");
        }
        if (maximumFailureBackoff.compareTo(initialFailureBackoff) < 0) {
            throw new IllegalArgumentException(
                    "maximumFailureBackoff cannot be below initialFailureBackoff");
        }
    }

    private static Duration requireDelay(
            Duration duration, String name, boolean zeroAllowed, Duration maximum) {
        Duration value = Objects.requireNonNull(duration, name);
        if (value.isNegative()
                || (!zeroAllowed && value.isZero())
                || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " is outside the safe range");
        }
        try {
            value.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " cannot be represented in nanoseconds", exception);
        }
        return value;
    }
}
