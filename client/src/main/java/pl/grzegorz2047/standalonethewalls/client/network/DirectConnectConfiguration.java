package pl.grzegorz2047.standalonethewalls.client.network;

import java.time.Duration;
import java.util.Objects;

/** Bounded process configuration for one Direct Connect service instance. */
public record DirectConnectConfiguration(
        Duration connectTimeout,
        Duration socketReadTimeout,
        Duration protocolStepTimeout,
        Duration closeTimeout,
        Duration confirmationLifetime) {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration MAXIMUM_CONFIRMATION_LIFETIME = Duration.ofMinutes(10);

    public static final DirectConnectConfiguration DEFAULT =
            new DirectConnectConfiguration(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(5),
                    Duration.ofMinutes(2));

    public DirectConnectConfiguration {
        connectTimeout = requireTimeout(connectTimeout, "connectTimeout", MAXIMUM_TIMEOUT);
        socketReadTimeout = requireTimeout(socketReadTimeout, "socketReadTimeout", MAXIMUM_TIMEOUT);
        protocolStepTimeout =
                requireTimeout(protocolStepTimeout, "protocolStepTimeout", MAXIMUM_TIMEOUT);
        closeTimeout = requireTimeout(closeTimeout, "closeTimeout", MAXIMUM_TIMEOUT);
        confirmationLifetime =
                requireTimeout(
                        confirmationLifetime,
                        "confirmationLifetime",
                        MAXIMUM_CONFIRMATION_LIFETIME);
    }

    private static Duration requireTimeout(Duration value, String field, Duration maximum) {
        Duration timeout = Objects.requireNonNull(value, field);
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(maximum) > 0
                || timeout.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " is outside the accepted range");
        }
        return timeout;
    }
}
