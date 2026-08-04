package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.Serial;
import java.util.Objects;

/** Stable fail-closed error raised while decoding preparation gameplay metadata. */
public final class PreparationGameplayException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationGameplayException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationGameplayException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        MALFORMED_JSON,
        UNKNOWN_FIELD,
        MISSING_FIELD,
        UNSUPPORTED_SCHEMA,
        INVALID_TEAM,
        INVALID_VALUE,
        INVALID_LAYOUT,
        TOO_MANY_ENTRIES
    }
}
