package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.time.Duration;
import java.util.Objects;

/** Hard bounds for process-local one-time realtime tickets. */
public record RealtimeTicketStoreConfig(int maximumActiveTickets, Duration maximumLifetime) {
    public static final int HARD_MAXIMUM_ACTIVE_TICKETS = 65_536;
    public static final Duration HARD_MAXIMUM_LIFETIME = Duration.ofMinutes(5);

    public RealtimeTicketStoreConfig {
        if (maximumActiveTickets < 1 || maximumActiveTickets > HARD_MAXIMUM_ACTIVE_TICKETS) {
            throw new IllegalArgumentException("maximumActiveTickets is outside hard bounds");
        }
        Objects.requireNonNull(maximumLifetime, "maximumLifetime");
        if (maximumLifetime.isZero()
                || maximumLifetime.isNegative()
                || maximumLifetime.compareTo(HARD_MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException("maximumLifetime is outside hard bounds");
        }
    }
}
