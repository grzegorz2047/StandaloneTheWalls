package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.io.Serial;
import java.util.Objects;

/** Bounded failure raised while validating or publishing a complete preparation spawn plan. */
public final class PreparationSpawnPublishException extends IllegalStateException {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationSpawnPublishException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationSpawnPublishException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EMPTY_PLAN,
        DUPLICATE_PARTICIPANT,
        CHANNEL_COVERAGE_MISMATCH,
        SEND_START_FAILED,
        SEND_FAILED,
        SEND_TIMEOUT,
        INTERRUPTED
    }
}
