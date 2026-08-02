package pl.grzegorz2047.standalonethewalls.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Logger;

/** Explicit operator entry point used only by the Gradle syncAssets task. */
public final class AssetPackSyncMain {
    private static final Logger LOGGER = Logger.getLogger(AssetPackSyncMain.class.getName());

    private AssetPackSyncMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("expected lock path and cache directory");
        }
        Path lockPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path cachePath = Path.of(arguments[1]).toAbsolutePath().normalize();
        AssetPackLock lock = readLock(lockPath);
        AssetPackSynchronizer synchronizer =
                new AssetPackSynchronizer(
                        cachePath,
                        new JdkHttpsAssetPackProvider(
                                Duration.ofSeconds(15), Duration.ofSeconds(60)));
        for (AssetPackReference reference : lock.packs()) {
            try {
                Path tree = synchronizer.sync(reference);
                LOGGER.info(
                        () ->
                                "Verified asset pack "
                                        + reference.id()
                                        + '@'
                                        + reference.version()
                                        + " at "
                                        + tree);
            } catch (AssetPackSyncException exception) {
                throw new IllegalStateException(
                        "asset synchronization failed with code " + exception.code(), exception);
            }
        }
        LOGGER.info(() -> "Asset synchronization completed for " + lock.packs().size() + " pack(s)");
    }

    private static AssetPackLock readLock(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        "asset lock must be a regular non-symbolic-link file");
            }
            long size = Files.size(path);
            if (size < 1L || size > AssetPackLockCodec.MAXIMUM_LOCK_BYTES) {
                throw new IllegalArgumentException("asset lock size is outside the safe range");
            }
            return AssetPackLockCodec.decode(Files.readAllBytes(path));
        } catch (IOException | AssetPackLockException exception) {
            throw new IllegalArgumentException("asset lock cannot be read or validated", exception);
        }
    }
}
