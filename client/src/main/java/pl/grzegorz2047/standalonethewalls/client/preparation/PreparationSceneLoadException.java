package pl.grzegorz2047.standalonethewalls.client.preparation;

/** Stable fail-closed reasons for refusing to enter an unverified preparation scene. */
public final class PreparationSceneLoadException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Code {
        BUNDLE_LOAD_FAILED,
        MAP_ID_MISMATCH,
        MAP_SHA256_MISMATCH,
        SCENE_INVALID,
        COLLISION_INVALID,
        REGION_MISSING,
        SPAWN_MISSING,
        SPAWN_STATE_MISMATCH
    }

    private final Code code;

    public PreparationSceneLoadException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public PreparationSceneLoadException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
