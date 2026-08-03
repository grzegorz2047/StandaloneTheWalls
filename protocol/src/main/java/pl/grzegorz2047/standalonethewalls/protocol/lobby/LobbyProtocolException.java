package pl.grzegorz2047.standalonethewalls.protocol.lobby;

/** Stable bounded failure for malformed minimal-lobby protocol payloads. */
public final class LobbyProtocolException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public LobbyProtocolException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public LobbyProtocolException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        UNSUPPORTED_SCHEMA,
        INVALID_REVISION,
        INVALID_REQUEST_ID,
        INVALID_MEMBER_COUNT,
        INVALID_PLAYER_ID,
        INVALID_HANDLE,
        INVALID_TEAM,
        INVALID_BOOLEAN,
        INVALID_READY_STATE,
        INVALID_OUTCOME,
        INVALID_TICK,
        INVALID_MATCH_PHASE,
        INVALID_ROUND_NUMBER,
        INVALID_CANCELLATION_REASON,
        INVALID_MATCH_STATE,
        DUPLICATE_MEMBER,
        NON_CANONICAL_ORDER,
        TRAILING_BYTES
    }
}
