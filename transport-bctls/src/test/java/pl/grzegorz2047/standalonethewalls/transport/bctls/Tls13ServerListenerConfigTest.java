package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Tls13ServerListenerConfigTest {
    private static final InetSocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 0);

    @Test
    void acceptsAResolvedEphemeralLoopbackEndpoint() {
        Tls13ServerListenerConfig config =
                new Tls13ServerListenerConfig(
                        LOOPBACK, 16, 2, 40, Duration.ofSeconds(2), Duration.ofSeconds(3));

        assertThat(config.bindAddress()).isEqualTo(LOOPBACK);
        assertThat(config.handshakeTimeoutMillis()).isEqualTo(2_000);
    }

    @Test
    void rejectsUnresolvedAndOutOfRangeValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        InetSocketAddress.createUnresolved("invalid.test", 1234),
                                        16,
                                        2,
                                        40,
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        LOOPBACK,
                                        0,
                                        2,
                                        40,
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        LOOPBACK,
                                        16,
                                        0,
                                        40,
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        LOOPBACK,
                                        16,
                                        2,
                                        0,
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> config(LOOPBACK, 16, 2, 40, Duration.ZERO, Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        LOOPBACK,
                                        16,
                                        2,
                                        40,
                                        Duration.ofNanos(1),
                                        Duration.ofSeconds(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                config(
                                        LOOPBACK,
                                        16,
                                        2,
                                        40,
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(31)));
    }

    private static Tls13ServerListenerConfig config(
            InetSocketAddress bindAddress,
            int backlog,
            int handshakes,
            int activeConnections,
            Duration handshakeTimeout,
            Duration shutdownTimeout) {
        return new Tls13ServerListenerConfig(
                bindAddress,
                backlog,
                handshakes,
                activeConnections,
                handshakeTimeout,
                shutdownTimeout);
    }
}
