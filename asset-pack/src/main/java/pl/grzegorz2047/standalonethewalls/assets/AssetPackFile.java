package pl.grzegorz2047.standalonethewalls.assets;

/** One exact file declared by a verified asset-pack manifest. */
public record AssetPackFile(String path, long size, String sha256) {
    public static final long MAXIMUM_FILE_BYTES = 256L * 1024L * 1024L;

    public AssetPackFile {
        path = AssetPackReference.requireRelativePath(path, "path");
        if (size < 0L || size > MAXIMUM_FILE_BYTES) {
            throw new IllegalArgumentException("asset file size is outside the safe range");
        }
        sha256 = AssetPackReference.requireDigest(sha256, "sha256");
    }
}
