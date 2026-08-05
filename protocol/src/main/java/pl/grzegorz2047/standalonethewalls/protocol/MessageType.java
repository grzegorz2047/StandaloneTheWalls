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
    LOBBY_SNAPSHOT(11, 4 * 1024, Channel.RELIABLE),
    LOBBY_SELECT_TEAM(12, 16, Channel.RELIABLE),
    LOBBY_SET_READY(13, 16, Channel.RELIABLE),
    LOBBY_COMMAND_RESULT(14, 32, Channel.RELIABLE),
    LOBBY_MATCH_SNAPSHOT(15, 64, Channel.RELIABLE),
    PREPARATION_SPAWN_ASSIGNMENT(16, 160, Channel.RELIABLE),
    REALTIME_TICKET_REQUEST(17, 16, Channel.RELIABLE),
    REALTIME_TICKET_RESULT(18, 96, Channel.RELIABLE);

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
            case 12 -> Optional.of(LOBBY_SELECT_TEAM);
            case 13 -> Optional.of(LOBBY_SET_READY);
            case 14 -> Optional.of(LOBBY_COMMAND_RESULT);
            case 15 -> Optional.of(LOBBY_MATCH_SNAPSHOT);
            case 16 -> Optional.of(PREPARATION_SPAWN_ASSIGNMENT);
            case 17 -> Optional.of(REALTIME_TICKET_REQUEST);
            case 18 -> Optional.of(REALTIME_TICKET_RESULT);
            default -> Optional.empty();
        };
    }

    public enum Channel {
        RELIABLE,
        REALTIME,
        BOTH
    }
}
