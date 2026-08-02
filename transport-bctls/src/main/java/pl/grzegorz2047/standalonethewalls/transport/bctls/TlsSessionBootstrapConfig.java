package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.time.Duration;
import java.util.Objects;

/** Bounded timeout for the fixed pre-envelope TLS session exchange. */
public record TlsSessionBootstrapConfig(Duration timeout) {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofSeconds(30);

    public static final TlsSessionBootstrapConfig DEFAULT =
            new TlsSessionBootstrapConfig(Duration.ofSeconds(5));

    public TlsSessionBootstrapConfig {
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "session bootstrap timeout must be positive and no longer than 30 seconds");
        }
        if (timeout.toMillis() < 1L) {
            throw new IllegalArgumentException(
                    "session bootstrap timeout must be at least 1 millisecond");
        }
    }

    int timeoutMillis() {
        return Math.toIntExact(timeout.toMillis());
    }
}
