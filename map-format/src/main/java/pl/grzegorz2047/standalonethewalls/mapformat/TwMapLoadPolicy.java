package pl.grzegorz2047.standalonethewalls.mapformat;

/** Local in-memory limits applied before a map manifest can narrow them further. */
public record TwMapLoadPolicy(
        int maximumArchiveBytes,
        int maximumUncompressedBytes,
        int maximumFiles,
        int maximumExpansionRatio) {
    public static final int MAXIMUM_IN_MEMORY_BYTES = 256 * 1024 * 1024;

    public TwMapLoadPolicy {
        if (maximumArchiveBytes < 1
                || maximumArchiveBytes > MapManifestValidator.MAXIMUM_ARCHIVE_BYTES
                || maximumUncompressedBytes < maximumArchiveBytes
                || maximumUncompressedBytes > MAXIMUM_IN_MEMORY_BYTES
                || maximumFiles < 1
                || maximumFiles > MapManifestValidator.MAXIMUM_FILES
                || maximumExpansionRatio < 1
                || maximumExpansionRatio > 1_000) {
            throw new IllegalArgumentException(".twmap load policy is outside supported limits");
        }
    }

    public static TwMapLoadPolicy runtimeDefaults() {
        return new TwMapLoadPolicy(64 * 1024 * 1024, 128 * 1024 * 1024, 256, 100);
    }
}
