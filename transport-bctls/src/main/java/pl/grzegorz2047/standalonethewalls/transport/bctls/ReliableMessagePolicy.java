package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

/** Rejects messages that are not valid on the ordered reliable channel. */
final class ReliableMessagePolicy {
    private ReliableMessagePolicy() {
        throw new AssertionError("No instances");
    }

    static void requireAllowed(MessageType messageType) throws ProtocolException {
        Objects.requireNonNull(messageType, "messageType");
        requireAllowed(messageType.channel());
    }

    static void requireAllowed(MessageType.Channel channel) throws ProtocolException {
        Objects.requireNonNull(channel, "channel");
        if (channel == MessageType.Channel.REALTIME) {
            throw new ProtocolException(
                    ProtocolException.Code.WRONG_CHANNEL,
                    "the message type is not allowed on the reliable channel");
        }
    }
}
