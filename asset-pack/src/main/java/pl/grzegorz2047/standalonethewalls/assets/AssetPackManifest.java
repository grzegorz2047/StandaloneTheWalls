package pl.grzegorz2047.standalonethewalls.assets;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical manifest of every extracted file and its redistributable license. */
public record AssetPackManifest(
        String packId, String version, String license, List<AssetPackFile> files) {
    public static final int MAXIMUM_FILES = 10_000;
    public static final long MAXIMUM_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final Set<String> ALLOWED_LICENSES =
            Set.of("CC0-1.0", "CC-BY-4.0", "OFL-1.1");

    public AssetPackManifest {
        packId = new AssetPackReference(
                        packId,
                        version,
                        1,
                        java.net.URI.create("https://assets.invalid/releases/validation-1.0.0.zip"),
                        1,
                        "0".repeat(64),
                        "manifest.json",
                        "0".repeat(64))
                .id();
        version = requireVersion(version);
        license = Objects.requireNonNull(license, "license");
        if (!ALLOWED_LICENSES.contains(license)) {
            throw new IllegalArgumentException("asset pack license is not allowed");
        }
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        if (files.isEmpty() || files.size() > MAXIMUM_FILES) {
            throw new IllegalArgumentException("asset manifest file count is outside the safe range");
        }
        Set<String> paths = new HashSet<>();
        String previous = null;
        long total = 0L;
        for (AssetPackFile file : files) {
            AssetPackFile current = Objects.requireNonNull(file, "file");
            if (!paths.add(current.path())) {
                throw new IllegalArgumentException("asset manifest contains a duplicate path");
            }
            if (previous != null && previous.compareTo(current.path()) >= 0) {
                throw new IllegalArgumentException("asset manifest files must be sorted by path");
            }
            previous = current.path();
            try {
                total = Math.addExact(total, current.size());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("asset manifest total size overflowed", exception);
            }
            if (total > MAXIMUM_TOTAL_BYTES) {
                throw new IllegalArgumentException("asset manifest total size exceeds the safe range");
            }
        }
    }

    private static String requireVersion(String value) {
        try {
            return new AssetPackReference(
                            "validation",
                            value,
                            1,
                            java.net.URI.create(
                                    "https://assets.invalid/releases/validation-1.0.0.zip"),
                            1,
                            "0".repeat(64),
                            "manifest.json",
                            "0".repeat(64))
                    .version();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("asset manifest version is invalid", exception);
        }
    }
}
