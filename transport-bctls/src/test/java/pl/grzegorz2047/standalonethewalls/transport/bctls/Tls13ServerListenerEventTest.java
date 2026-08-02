package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class Tls13ServerListenerEventTest {
    @Test
    void publicTextDoesNotExposeFailureMessages() {
        IOException failure = new IOException("private diagnostic marker");
        Tls13ServerListenerEvent event =
                Tls13ServerListenerEvent.failed(
                        Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED,
                        new InetSocketAddress("127.0.0.1", 1234),
                        failure);

        assertThat(event.failure()).contains(failure);
        assertThat(event.toString())
                .contains("HANDSHAKE_FAILED")
                .contains(IOException.class.getName())
                .doesNotContain("private diagnostic marker");
    }

    @Test
    void limitEventsCannotCarryFailuresAndFailureEventsRequireOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                Tls13ServerListenerEvent.failed(
                                        Tls13ServerListenerEvent.Code.ACTIVE_CONNECTION_LIMIT,
                                        null,
                                        new IOException("not allowed")));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                Tls13ServerListenerEvent.rejected(
                                        Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED,
                                        null));
    }
}
