package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.io.Serial;
import java.util.Objects;

/** Checked rejection of one malformed or unsupported preparation protocol payload. */
public final class PreparationProtocolException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationProtocolException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationProtocolException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SIZE,
        UNSUPPORTED_SCHEMA,
        INVALID_REVISION,
        INVALID_ROUND_NUMBER,
        INVALID_MAP_ID,
        INVALID_MAP_DIGEST,
        INVALID_TEAM,
        INVALID_SPAWN_INDEX,
        INVALID_COORDINATE,
        INVALID_STATE
    }
}
