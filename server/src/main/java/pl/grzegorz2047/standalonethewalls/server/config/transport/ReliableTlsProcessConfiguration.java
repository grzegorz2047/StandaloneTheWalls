package pl.grzegorz2047.standalonethewalls.server.config.transport;

import java.time.Duration;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerCredentials;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListenerConfig;

/** Validated process-owned configuration for the reliable TLS admission endpoint. */
public record ReliableTlsProcessConfiguration(
        Tls13ServerListenerConfig listenerConfig,
        Tls13ServerCredentials credentials,
        Duration challengeLifetime,
        int maximumOutstandingChallenges,
        Duration resultSendTimeout,
        Duration gatewayShutdownTimeout) {
    private static final Duration MAXIMUM_CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration MAXIMUM_RESULT_SEND_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration MAXIMUM_GATEWAY_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    public ReliableTlsProcessConfiguration {
        listenerConfig = Objects.requireNonNull(listenerConfig, "listenerConfig");
        credentials = Objects.requireNonNull(credentials, "credentials");
        challengeLifetime =
                requireDuration(challengeLifetime, MAXIMUM_CHALLENGE_LIFETIME, "challengeLifetime");
        if (maximumOutstandingChallenges < 1 || maximumOutstandingChallenges > 100_000) {
            throw new IllegalArgumentException(
                    "maximumOutstandingChallenges is outside the safe range");
        }
        resultSendTimeout =
                requireDuration(
                        resultSendTimeout, MAXIMUM_RESULT_SEND_TIMEOUT, "resultSendTimeout");
        gatewayShutdownTimeout =
                requireDuration(
                        gatewayShutdownTimeout,
                        MAXIMUM_GATEWAY_SHUTDOWN_TIMEOUT,
                        "gatewayShutdownTimeout");
    }

    private static Duration requireDuration(Duration value, Duration maximum, String field) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero()
                || duration.isNegative()
                || duration.compareTo(maximum) > 0
                || duration.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        return duration;
    }
}
