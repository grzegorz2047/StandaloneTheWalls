package pl.grzegorz2047.standalonethewalls.registry;

import java.time.Duration;
import java.util.Objects;

/** Local acceptance limits applied after cryptographic artifact verification. */
public record RegistrySnapshotPolicy(
        long minimumSequence,
        Duration maximumAge,
        Duration maximumFutureSkew,
        int maximumJsonBytes,
        int maximumEntries) {
    public static final int ABSOLUTE_MAXIMUM_JSON_BYTES = 16 * 1024 * 1024;
    public static final int ABSOLUTE_MAXIMUM_ENTRIES = 250_000;
    public static final RegistrySnapshotPolicy DEFAULT =
            new RegistrySnapshotPolicy(
                    0L,
                    Duration.ofDays(30),
                    Duration.ofMinutes(5),
                    8 * 1024 * 1024,
                    100_000);

    public RegistrySnapshotPolicy {
        maximumAge = requireDuration(maximumAge, "maximumAge", Duration.ofDays(365));
        maximumFutureSkew =
                requireDuration(maximumFutureSkew, "maximumFutureSkew", Duration.ofHours(24));
        if (minimumSequence < 0L) {
            throw new IllegalArgumentException("minimumSequence cannot be negative");
        }
        if (maximumJsonBytes < 1 || maximumJsonBytes > ABSOLUTE_MAXIMUM_JSON_BYTES) {
            throw new IllegalArgumentException("maximumJsonBytes is outside the safe range");
        }
        if (maximumEntries < 0 || maximumEntries > ABSOLUTE_MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("maximumEntries is outside the safe range");
        }
    }

    private static Duration requireDuration(
            Duration value, String field, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isNegative() || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        return duration;
    }
}
