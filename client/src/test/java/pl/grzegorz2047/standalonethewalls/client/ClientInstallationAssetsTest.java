package pl.grzegorz2047.standalonethewalls.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientInstallationAssetsTest {
    @TempDir Path tempDirectory;

    @Test
    void resolvesRelocatedJpackageAssetLockWithoutUsingWorkingDirectory() throws IOException {
        Path imageRoot = tempDirectory.resolve("relocated").resolve("Sunderfront");
        Path launcher = imageRoot.resolve("Sunderfront.exe");
        assertEquals(imageRoot, Files.createDirectories(imageRoot));
        assertEquals(launcher, Files.createFile(launcher));

        assertEquals(
                imageRoot.resolve(ClientInstallationAssets.ASSET_LOCK_RELATIVE_PATH),
                ClientInstallationAssets.resolveAssetLock(launcher.toString(), null).orElseThrow());
    }

    @Test
    void resolvesJvmAndJpackageJarLayoutsFromCodeSource() throws IOException {
        assertJarLayout("lib");
        assertJarLayout("app");
    }

    @Test
    void doesNotGuessFromClassDirectoriesUnknownContainersOrMalformedLocations() throws Exception {
        Path classes = Files.createDirectories(tempDirectory.resolve("classes"));
        Path unknown = Files.createDirectories(tempDirectory.resolve("unknown"));
        Path unknownJar = Files.createFile(unknown.resolve("client.jar"));
        URL httpLocation = URI.create("https://example.invalid/client.jar").toURL();

        assertTrue(
                ClientInstallationAssets.resolveAssetLock(null, classes.toUri().toURL()).isEmpty());
        assertTrue(
                ClientInstallationAssets.resolveAssetLock(null, unknownJar.toUri().toURL())
                        .isEmpty());
        assertTrue(ClientInstallationAssets.resolveAssetLock(null, httpLocation).isEmpty());
        assertTrue(ClientInstallationAssets.resolveAssetLock(null, null).isEmpty());
        assertTrue(
                ClientInstallationAssets.resolveAssetLock("missing/Sunderfront.exe", null)
                        .isEmpty());
    }

    private void assertJarLayout(String containerName) throws IOException {
        Path root = tempDirectory.resolve("distribution-" + containerName);
        Path container = root.resolve(containerName);
        assertEquals(container, Files.createDirectories(container));
        Path jar = container.resolve("client.jar");
        assertEquals(jar, Files.createFile(jar));

        assertEquals(
                root.resolve(ClientInstallationAssets.ASSET_LOCK_RELATIVE_PATH),
                ClientInstallationAssets.resolveAssetLock(null, jar.toUri().toURL()).orElseThrow());
    }
}
