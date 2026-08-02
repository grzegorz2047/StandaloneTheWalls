package pl.grzegorz2047.standalonethewalls.protocol.identity;

/** Safe semantic failure while decoding one identity payload. */
public final class IdentityPayloadException extends Exception {
    private final Code code;

    public IdentityPayloadException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public IdentityPayloadException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        UNSUPPORTED_SCHEMA,
        INVALID_LENGTH,
        INVALID_TEXT,
        INVALID_EXPIRATION,
        INVALID_PUBLIC_KEY,
        INVALID_SIGNATURE,
        INVALID_STATUS,
        STATUS_CODE_MISMATCH,
        TRAILING_BYTES
    }
}
