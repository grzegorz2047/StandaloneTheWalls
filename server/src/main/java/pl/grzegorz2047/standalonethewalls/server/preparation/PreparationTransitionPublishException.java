package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.io.Serial;
import java.util.Objects;

/** Terminal failure raised while publishing the authoritative preparation transition. */
public final class PreparationTransitionPublishException extends IllegalStateException {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationTransitionPublishException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationTransitionPublishException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        CHANNEL_COVERAGE_MISMATCH,
        SNAPSHOT_SEND_START_FAILED,
        SNAPSHOT_SEND_FAILED,
        TIMEOUT,
        INTERRUPTED,
        ASSIGNMENT_PUBLISH_FAILED
    }
}
