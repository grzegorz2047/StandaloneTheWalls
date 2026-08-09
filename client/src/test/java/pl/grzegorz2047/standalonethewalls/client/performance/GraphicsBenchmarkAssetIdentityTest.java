package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkAssetIdentityTest {
    private static final byte[] LOCK_BYTES = "{\"packs\":[],\"schema\":1}".getBytes();

    @TempDir Path tempDirectory;

    @Test
    void identicalBytesProduceTheSameIdentityAndCurrentScenarioKey() throws IOException {
        Path first = write("first.lock", LOCK_BYTES);
        Path second = write("second.lock", LOCK_BYTES);

        GraphicsBenchmarkAssetIdentity firstIdentity =
                GraphicsBenchmarkAssetIdentity.fromLock(first);
        GraphicsBenchmarkAssetIdentity secondIdentity =
                GraphicsBenchmarkAssetIdentity.fromLock(second);

        assertThat(firstIdentity.assetSetId())
                .isEqualTo(GraphicsBenchmarkAssetIdentity.ASSET_SET_ID);
        assertThat(firstIdentity.assetSetVersion()).isEqualTo(secondIdentity.assetSetVersion());
        assertThat(firstIdentity.assetSetVersion()).startsWith("sha256:");
        assertThat(firstIdentity.assetSetVersion().substring("sha256:".length()))
                .matches("[0-9a-f]{64}");
        assertThat(firstIdentity.compatibilityKey()).isEqualTo(secondIdentity.compatibilityKey());
        assertThat(firstIdentity.compatibilityKey().scenarioId())
                .isEqualTo(GraphicsBenchmarkReferenceScene.SCENARIO_ID);
        assertThat(firstIdentity.compatibilityKey().scenarioVersion())
                .isEqualTo(GraphicsBenchmarkReferenceScene.SCENARIO_VERSION);
    }

    @Test
    void anyByteChangeProducesADifferentVersionAndCompatibilityKey() throws IOException {
        Path first = write("first.lock", LOCK_BYTES);
        byte[] changed = LOCK_BYTES.clone();
        changed[changed.length - 1] ^= 1;
        Path second = write("second.lock", changed);

        GraphicsBenchmarkAssetIdentity firstIdentity =
                GraphicsBenchmarkAssetIdentity.fromLock(first);
        GraphicsBenchmarkAssetIdentity secondIdentity =
                GraphicsBenchmarkAssetIdentity.fromLock(second);

        assertThat(secondIdentity.assetSetVersion()).isNotEqualTo(firstIdentity.assetSetVersion());
        assertThat(secondIdentity.compatibilityKey()).isNotEqualTo(firstIdentity.compatibilityKey());
    }

    @Test
    void readingIdentityDoesNotMutateTheLockFile() throws IOException {
        Path lock = write("assets.lock.json", LOCK_BYTES);
        byte[] before = Files.readAllBytes(lock);

        GraphicsBenchmarkAssetIdentity identity = GraphicsBenchmarkAssetIdentity.fromLock(lock);

        assertThat(identity.assetSetVersion()).startsWith("sha256:");
        assertThat(Files.readAllBytes(lock)).containsExactly(before);
    }

    @Test
    void rejectsMissingDirectoryEmptyOversizedAndNullPaths() throws IOException {
        Path missing = tempDirectory.resolve("missing.lock");
        Path directory = tempDirectory.resolve("directory");
        assertThat(Files.createDirectory(directory)).isEqualTo(directory);
        Path empty = write("empty.lock", new byte[0]);
        byte[] oversized =
                new byte[Math.toIntExact(GraphicsBenchmarkAssetIdentity.MAXIMUM_LOCK_BYTES) + 1];
        Arrays.fill(oversized, (byte) 1);
        Path oversizedFile = write("oversized.lock", oversized);

        assertThatNullPointerException()
                .isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(null));
        assertThatIOException().isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(missing));
        assertThatIOException().isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(directory));
        assertThatIOException().isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(empty));
        assertThatIOException()
                .isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(oversizedFile));
    }

    @Test
    void rejectsSymbolicLinksWhenTheFileSystemSupportsThem() throws IOException {
        Path target = write("target.lock", LOCK_BYTES);
        Path link = tempDirectory.resolve("link.lock");
        try {
            assertThat(Files.createSymbolicLink(link, target.getFileName())).isEqualTo(link);
        } catch (UnsupportedOperationException | FileSystemException exception) {
            assumeTrue(false, "symbolic links are not supported by this test file system");
        }

        assertThatIOException().isThrownBy(() -> GraphicsBenchmarkAssetIdentity.fromLock(link));
    }

    private Path write(String fileName, byte[] bytes) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        assertThat(Files.write(path, bytes)).isEqualTo(path);
        return path;
    }
}
