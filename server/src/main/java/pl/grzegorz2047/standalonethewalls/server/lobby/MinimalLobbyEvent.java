package pl.grzegorz2047.standalonethewalls.server.lobby;

/** Bounded operational event without addresses, keys, proofs, or exception text. */
public record MinimalLobbyEvent(Code code, int memberCount, long revision) {
    public MinimalLobbyEvent {
        if (memberCount < 0 || memberCount > 40) {
            throw new IllegalArgumentException("memberCount is outside the supported range");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public enum Code {
        MEMBER_JOINED,
        MEMBER_LEFT,
        DUPLICATE_PLAYER_REJECTED,
        PROTOCOL_VIOLATION,
        SEND_FAILED,
        RECEIVE_FAILED,
        INTERNAL_FAILURE,
        RUNTIME_CLOSED
    }
}
