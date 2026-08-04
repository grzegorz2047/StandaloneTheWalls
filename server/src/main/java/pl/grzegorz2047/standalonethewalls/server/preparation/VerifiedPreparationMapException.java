package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.io.Serial;
import java.util.Objects;

/** Terminal fail-closed error while adapting a verified bundle for server preparation. */
public final class VerifiedPreparationMapException extends Exception {
    @Serial private static final long serialVersionUID = 1L;

    private final Code code;

    public VerifiedPreparationMapException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public VerifiedPreparationMapException(Code code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_SCENE,
        INVALID_COLLISION,
        MANIFEST_GAMEPLAY_MISMATCH,
        INSUFFICIENT_TEAM_SPAWNS
    }
}
