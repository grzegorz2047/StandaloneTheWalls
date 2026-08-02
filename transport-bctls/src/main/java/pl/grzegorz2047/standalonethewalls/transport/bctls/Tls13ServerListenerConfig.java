package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;

/** Hard listener, admission, handshake and shutdown limits for one TLS endpoint. */
public record Tls13ServerListenerConfig(
        InetSocketAddress bindAddress,
        int backlog,
        int maximumConcurrentHandshakes,
        int maximumActiveConnections,
        Duration handshakeTimeout,
        Duration shutdownTimeout) {
    private static final int MAXIMUM_BACKLOG = 4096;
    private static final int MAXIMUM_CONCURRENT_HANDSHAKES = 256;
    private static final int MAXIMUM_ACTIVE_CONNECTIONS = 4096;
    private static final Duration MAXIMUM_HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAXIMUM_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    public Tls13ServerListenerConfig {
        bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        if (bindAddress.isUnresolved()) {
            throw new IllegalArgumentException("bind address must be resolved");
        }
        int port = bindAddress.getPort();
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("bind port must be between 0 and 65535");
        }
        if (backlog < 1 || backlog > MAXIMUM_BACKLOG) {
            throw new IllegalArgumentException("backlog must be between 1 and 4096");
        }
        if (maximumConcurrentHandshakes < 1
                || maximumConcurrentHandshakes > MAXIMUM_CONCURRENT_HANDSHAKES) {
            throw new IllegalArgumentException(
                    "maximum concurrent handshakes must be between 1 and 256");
        }
        if (maximumActiveConnections < 1
                || maximumActiveConnections > MAXIMUM_ACTIVE_CONNECTIONS) {
            throw new IllegalArgumentException(
                    "maximum active connections must be between 1 and 4096");
        }
        handshakeTimeout = requireDuration(
                handshakeTimeout,
                MAXIMUM_HANDSHAKE_TIMEOUT,
                "handshake timeout");
        shutdownTimeout = requireDuration(
                shutdownTimeout,
                MAXIMUM_SHUTDOWN_TIMEOUT,
                "shutdown timeout");
    }

    public static Tls13ServerListenerConfig localDefault(int port, int maximumConnections) {
        return new Tls13ServerListenerConfig(
                new InetSocketAddress("0.0.0.0", port),
                128,
                Math.min(16, maximumConnections),
                maximumConnections,
                Duration.ofSeconds(10),
                Duration.ofSeconds(5));
    }

    int handshakeTimeoutMillis() {
        return Math.toIntExact(handshakeTimeout.toMillis());
    }

    private static Duration requireDuration(Duration value, Duration maximum, String field) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    field + " must be positive and no longer than " + maximum.toSeconds() + " seconds");
        }
        if (duration.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " must be at least 1 millisecond");
        }
        return duration;
    }
}
