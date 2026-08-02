package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.Optional;

/** Explicit v1 message catalog. Gameplay messages are introduced by later issues. */
public enum MessageType {
    CLIENT_HELLO(1, 4 * 1024, Channel.RELIABLE),
    SERVER_HELLO(2, 4 * 1024, Channel.RELIABLE),
    PING(3, 64, Channel.BOTH),
    PONG(4, 64, Channel.BOTH),
    DISCONNECT(5, 2 * 1024, Channel.RELIABLE),
    IDENTITY_CHALLENGE(6, 2 * 1024, Channel.RELIABLE),
    IDENTITY_PROOF(7, 4 * 1024, Channel.RELIABLE),
    IDENTITY_RESULT(8, 1024, Channel.RELIABLE),
    SESSION_ADMISSION_RESULT(9, 64, Channel.RELIABLE),
    LOBBY_JOINED(10, 128, Channel.RELIABLE),
    LOBBY_SNAPSHOT(11, 4 * 1024, Channel.RELIABLE);

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
            case 6 -> Optional.of(IDENTITY_CHALLENGE);
            case 7 -> Optional.of(IDENTITY_PROOF);
            case 8 -> Optional.of(IDENTITY_RESULT);
            case 9 -> Optional.of(SESSION_ADMISSION_RESULT);
            case 10 -> Optional.of(LOBBY_JOINED);
            case 11 -> Optional.of(LOBBY_SNAPSHOT);
            default -> Optional.empty();
        };
    }

    public enum Channel {
        RELIABLE,
        REALTIME,
        BOTH
    }
}
