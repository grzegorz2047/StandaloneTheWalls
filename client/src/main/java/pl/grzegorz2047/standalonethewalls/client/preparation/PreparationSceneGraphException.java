package pl.grzegorz2047.standalonethewalls.client.preparation;

/** Fail-closed error raised when verified GLB bytes cannot become a jMonkeyEngine scene graph. */
public final class PreparationSceneGraphException extends Exception {
    private static final long serialVersionUID = 1L;

    public PreparationSceneGraphException(String message, Throwable cause) {
        super(message, cause);
    }

    public PreparationSceneGraphException(String message) {
        super(message);
    }
}
