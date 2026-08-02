package pl.grzegorz2047.standalonethewalls.transport.bctls;

/** Bounded pre-envelope session-bootstrap failure that never includes record bytes. */
public final class TlsSessionBootstrapException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public TlsSessionBootstrapException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public TlsSessionBootstrapException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_RECORD_SIZE,
        TRUNCATED_RECORD,
        INVALID_MAGIC,
        UNSUPPORTED_SCHEMA,
        UNEXPECTED_RECORD_TYPE,
        UNSUPPORTED_PROTOCOL,
        INVALID_SESSION_ID,
        SESSION_MISMATCH,
        TIMEOUT
    }
}
