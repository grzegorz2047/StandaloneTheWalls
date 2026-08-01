package pl.grzegorz2047.standalonethewalls.mapformat;

/** Validated package and rendering budgets declared by a map. */
public record MapLimits(
        long archiveBytes,
        long uncompressedBytes,
        int fileCount,
        int sceneNodes,
        int triangles,
        int textureDimension) {

    public MapLimits {
        if (archiveBytes < 1L
                || uncompressedBytes < archiveBytes
                || fileCount < 1
                || sceneNodes < 1
                || triangles < 1
                || textureDimension < 1
                || (textureDimension & (textureDimension - 1)) != 0) {
            throw new IllegalArgumentException("validated limits must be positive and internally consistent");
        }
    }
}
