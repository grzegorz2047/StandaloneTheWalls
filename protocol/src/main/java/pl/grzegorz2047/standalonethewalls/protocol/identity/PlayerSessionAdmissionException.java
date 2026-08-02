package pl.grzegorz2047.standalonethewalls.protocol.identity;

/** Safe semantic failure while decoding one post-authentication admission payload. */
public final class PlayerSessionAdmissionException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public PlayerSessionAdmissionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        UNSUPPORTED_SCHEMA,
        INVALID_STATUS
    }
}
