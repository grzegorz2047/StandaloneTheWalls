package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;

/** Bounded fail-closed rejection produced before any preparation spawn is published. */
public final class PreparationSpawnAllocationException extends IllegalArgumentException {
    private final Code code;

    public PreparationSpawnAllocationException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EMPTY_ROSTER,
        UNASSIGNED_PARTICIPANT,
        DUPLICATE_SPAWN_INDEX,
        INSUFFICIENT_TEAM_SPAWNS
    }
}
