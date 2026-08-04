package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.io.Serial;
import java.util.Objects;

/** Bounded fail-closed rejection raised before any preparation assignment is sent. */
public final class PreparationTransitionPlanException extends IllegalArgumentException {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public PreparationTransitionPlanException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public PreparationTransitionPlanException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_PHASE,
        ROSTER_REVISION_MISMATCH,
        PLAYER_COUNT_MISMATCH,
        SPAWN_ALLOCATION_FAILED,
        ASSIGNMENT_COUNT_MISMATCH
    }
}
