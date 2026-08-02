package pl.grzegorz2047.standalonethewalls.protocol;

/** Bounded protocol failure. Raw payload content is deliberately excluded. */
public final class ProtocolException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public ProtocolException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_MAGIC,
        UNSUPPORTED_VERSION,
        UNKNOWN_MESSAGE_TYPE,
        INVALID_FLAGS,
        INVALID_SEQUENCE,
        INVALID_LENGTH,
        TRUNCATED_MESSAGE,
        TRAILING_BYTES,
        SESSION_MISMATCH,
        OUT_OF_ORDER_SEQUENCE,
        SEQUENCE_EXHAUSTED,
        WRONG_CHANNEL
    }
}
