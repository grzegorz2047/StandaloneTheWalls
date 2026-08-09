package pl.grzegorz2047.standalonethewalls.mapformat;

/** Renderer-independent map guardrails for the Low integrated-GPU quality profile. */
public final class LowProfileMapBudget {
    public static final int MAX_ARCHIVE_BYTES = 1_048_576;
    public static final int MAX_MEMBER_FILE_COUNT = 5;
    public static final int MAX_SCENE_NODES = 64;
    public static final int MAX_TEXTURE_DIMENSION = 64;
    public static final int MAX_TRIANGLES = 256;
    public static final int MAX_UNCOMPRESSED_MEMBER_BYTES = 2_097_152;
    public static final int MAX_SCENE_LIGHTS = 4;

    private LowProfileMapBudget() {
        throw new AssertionError("No instances");
    }

    static String manifestLimitsJson() {
        return "{\"archiveBytes\":"
                + MAX_ARCHIVE_BYTES
                + ",\"fileCount\":"
                + MAX_MEMBER_FILE_COUNT
                + ",\"sceneNodes\":"
                + MAX_SCENE_NODES
                + ",\"textureDimension\":"
                + MAX_TEXTURE_DIMENSION
                + ",\"triangles\":"
                + MAX_TRIANGLES
                + ",\"uncompressedBytes\":"
                + MAX_UNCOMPRESSED_MEMBER_BYTES
                + '}';
    }
}
