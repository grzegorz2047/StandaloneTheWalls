package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.util.Objects;

/** Stable bounded codec failure for realtime ticket messages. */
public final class RealtimeTicketProtocolException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public RealtimeTicketProtocolException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        UNSUPPORTED_SCHEMA,
        INVALID_REQUEST_ID,
        INVALID_PROFILE,
        INVALID_STATUS,
        INVALID_REJECTION,
        INVALID_EXPIRATION
    }
}
