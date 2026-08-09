package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Exact bundled-asset identity used to invalidate stale benchmark results. */
public final class GraphicsBenchmarkAssetIdentity {
    public static final String ASSET_SET_ID = "bundled-assets-lock";
    public static final long MAXIMUM_LOCK_BYTES = 1_048_576L;

    private static final String VERSION_PREFIX = "sha256:";

    private final String assetSetVersion;

    private GraphicsBenchmarkAssetIdentity(String assetSetVersion) {
        this.assetSetVersion = Objects.requireNonNull(assetSetVersion, "assetSetVersion");
    }

    public static GraphicsBenchmarkAssetIdentity fromLock(Path lockFile) throws IOException {
        Objects.requireNonNull(lockFile, "lockFile");
        if (Files.isSymbolicLink(lockFile)
                || !Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("asset lock must be a regular non-symlink file");
        }

        long expectedSize = Files.size(lockFile);
        if (expectedSize < 1L || expectedSize > MAXIMUM_LOCK_BYTES) {
            throw new IOException("asset lock size is outside the bounded range");
        }
        byte[] bytes = Files.readAllBytes(lockFile);
        if (bytes.length != expectedSize) {
            throw new IOException("asset lock changed while being read");
        }
        return new GraphicsBenchmarkAssetIdentity(VERSION_PREFIX + sha256(bytes));
    }

    public String assetSetId() {
        return ASSET_SET_ID;
    }

    public String assetSetVersion() {
        return assetSetVersion;
    }

    public GraphicsBenchmarkCompatibilityKey compatibilityKey() {
        return new GraphicsBenchmarkCompatibilityKey(
                ASSET_SET_ID,
                assetSetVersion,
                GraphicsBenchmarkReferenceScene.SCENARIO_ID,
                GraphicsBenchmarkReferenceScene.SCENARIO_VERSION);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java platform", exception);
        }
    }
}
