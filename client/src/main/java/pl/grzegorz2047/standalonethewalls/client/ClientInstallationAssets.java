package pl.grzegorz2047.standalonethewalls.client;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Objects;
import java.util.Optional;

/** Resolves immutable packaged client assets from verified installation layouts. */
final class ClientInstallationAssets {
    static final String ASSET_LOCK_RELATIVE_PATH = "assets/assets.lock.json";
    private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";

    private ClientInstallationAssets() {
        throw new AssertionError("No instances");
    }

    static Optional<Path> resolveAssetLock(Class<?> anchor) {
        Objects.requireNonNull(anchor, "anchor");
        CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        return resolveAssetLock(System.getProperty(JPACKAGE_APP_PATH_PROPERTY), location);
    }

    static Optional<Path> resolveAssetLock(String packagedLauncherPath, URL codeSourceLocation) {
        Optional<Path> packaged = resolveJpackageAssetLock(packagedLauncherPath);
        if (packaged.isPresent()) {
            return packaged;
        }
        return resolveJvmDistributionAssetLock(codeSourceLocation);
    }

    private static Optional<Path> resolveJpackageAssetLock(String packagedLauncherPath) {
        if (packagedLauncherPath == null || packagedLauncherPath.isBlank()) {
            return Optional.empty();
        }
        final Path launcher;
        try {
            launcher = Path.of(packagedLauncherPath).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(launcher)) {
            return Optional.empty();
        }
        Path root = launcher.getParent();
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(root.resolve(ASSET_LOCK_RELATIVE_PATH).normalize());
    }

    private static Optional<Path> resolveJvmDistributionAssetLock(URL codeSourceLocation) {
        if (codeSourceLocation == null || !"file".equalsIgnoreCase(codeSourceLocation.getProtocol())) {
            return Optional.empty();
        }
        final Path codeLocation;
        try {
            codeLocation = Path.of(codeSourceLocation.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(codeLocation)) {
            return Optional.empty();
        }
        Path container = codeLocation.getParent();
        if (container == null || container.getFileName() == null) {
            return Optional.empty();
        }
        String containerName = container.getFileName().toString();
        if (!"lib".equals(containerName) && !"app".equals(containerName)) {
            return Optional.empty();
        }
        Path root = container.getParent();
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(root.resolve(ASSET_LOCK_RELATIVE_PATH).normalize());
    }
}
