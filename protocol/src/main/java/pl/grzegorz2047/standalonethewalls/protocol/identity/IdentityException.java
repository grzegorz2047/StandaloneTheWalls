package pl.grzegorz2047.standalonethewalls.protocol.identity;

/** Security-sensitive identity operation failure without raw key material. */
public final class IdentityException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public IdentityException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public IdentityException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        KEY_GENERATION_FAILED,
        KEY_ENCODING_UNAVAILABLE,
        INVALID_PUBLIC_KEY,
        INVALID_KEY_PAIR,
        SIGNING_FAILED,
        VERIFICATION_FAILED,
        KEY_STORE_READ_FAILED,
        KEY_STORE_WRITE_FAILED,
        KEY_STORE_INVALID,
        KEY_STORE_CONFLICT
    }
}
