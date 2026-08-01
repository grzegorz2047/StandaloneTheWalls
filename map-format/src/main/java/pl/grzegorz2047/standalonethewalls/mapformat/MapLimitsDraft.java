package pl.grzegorz2047.standalonethewalls.mapformat;

/** Untrusted resource-budget declarations parsed from a manifest. */
public record MapLimitsDraft(
        Long archiveBytes,
        Long uncompressedBytes,
        Integer fileCount,
        Integer sceneNodes,
        Integer triangles,
        Integer textureDimension) {}
