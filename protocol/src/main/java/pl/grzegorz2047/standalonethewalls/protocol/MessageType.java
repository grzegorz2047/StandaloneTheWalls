package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.Optional;

/** Explicit v1 message catalog. Gameplay messages are introduced by later issues. */
public enum MessageType {
    CLIENT_HELLO(1, 4 * 1024, Channel.RELIABLE),
    SERVER_HELLO(2, 4 * 1024, Channel.RELIABLE),
    PING(3, 64, Channel.BOTH),
    PONG(4, 64, Channel.BOTH),
    DISCONNECT(5, 2 * 1024, Channel.RELIABLE);

    private final int wireId;
    private final int maximumPayloadBytes;
    private final Channel channel;

    MessageType(int wireId, int maximumPayloadBytes, Channel channel) {
        this.wireId = wireId;
        this.maximumPayloadBytes = maximumPayloadBytes;
        this.channel = channel;
    }

    public int wireId() {
        return wireId;
    }

    public int maximumPayloadBytes() {
        return maximumPayloadBytes;
    }

    public Channel channel() {
        return channel;
    }

    public static Optional<MessageType> fromWireId(int wireId) {
        return switch (wireId) {
            case 1 -> Optional.of(CLIENT_HELLO);
            case 2 -> Optional.of(SERVER_HELLO);
            case 3 -> Optional.of(PING);
            case 4 -> Optional.of(PONG);
            case 5 -> Optional.of(DISCONNECT);
            default -> Optional.empty();
        };
    }

    public enum Channel {
        RELIABLE,
        REALTIME,
        BOTH
    }
}
