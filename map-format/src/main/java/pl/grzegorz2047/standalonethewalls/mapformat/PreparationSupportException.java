package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.Serial;
import java.util.Objects;

/** Stable fail-closed error raised while deriving support boxes from a verified collision GLB. */
public final class PreparationSupportException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationSupportException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationSupportException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        MALFORMED_JSON,
        MISSING_LAYOUT,
        INVALID_NODE,
        INVALID_MESH,
        INVALID_ACCESSOR,
        DUPLICATE_NAME,
        TOO_MANY_SUPPORTS
    }
}
