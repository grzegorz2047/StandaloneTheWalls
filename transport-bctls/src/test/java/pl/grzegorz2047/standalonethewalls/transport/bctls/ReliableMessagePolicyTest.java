package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

class ReliableMessagePolicyTest {
    @Test
    void acceptsReliableAndDualChannelMessages() {
        assertThatCode(() -> ReliableMessagePolicy.requireAllowed(MessageType.Channel.RELIABLE))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReliableMessagePolicy.requireAllowed(MessageType.Channel.BOTH))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRealtimeOnlyMessages() {
        assertThatThrownBy(
                        () ->
                                ReliableMessagePolicy.requireAllowed(
                                        MessageType.Channel.REALTIME))
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ProtocolException.Code.WRONG_CHANNEL));
    }
}
