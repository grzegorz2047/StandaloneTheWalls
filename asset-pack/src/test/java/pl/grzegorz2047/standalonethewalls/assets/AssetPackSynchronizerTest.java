package pl.grzegorz2047.standalonethewalls.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetPackSynchronizerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void synchronizesTwiceAndRestartsOfflineFromTheSameVerifiedTree() throws Exception {
        PackFixture fixture =
                createPack(
                        "1.0.0",
                        Map.of(
                                "models/cube.txt", bytes("cube-v1"),
                                "textures/stone.txt", bytes("stone-v1")),
                        "CC0-1.0");
        Path cache = temporaryDirectory.resolve("cache");
        AssetPackSynchronizer synchronizer =
                new AssetPackSynchronizer(
                        cache,
                        new FileAssetPackProvider(
                                Map.of(fixture.reference().url(), fixture.archive())));

        Path first = synchronizer.sync(fixture.reference());
        Path second = synchronizer.sync(fixture.reference());

        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(first.resolve("models/cube.txt"))).isEqualTo("cube-v1");
        assertThat(Files.readString(first.resolve("textures/stone.txt"))).isEqualTo("stone-v1");

        AssetPackSynchronizer offline =
                new AssetPackSynchronizer(
                        cache,
                        ignored -> {
                            throw new IOException("network must not be used for offline resolution");
                        });
        assertThat(offline.resolveOffline(fixture.reference())).isEqualTo(first);
    }

    @Test
    void failedReplacementPreservesLastKnownGoodAndStaleLocksAreExplicit() throws Exception {
        PackFixture first = createPack("1.0.0", Map.of("data/value.txt", bytes("one")), "CC0-1.0");
        PackFixture second = createPack("1.1.0", Map.of("data/value.txt", bytes("two")), "CC0-1.0");
        Path cache = temporaryDirectory.resolve("cache");
        AssetPackSynchronizer firstSync =
                synchronizer(cache, Map.of(first.reference().url(), first.archive()));
        Path firstTree = firstSync.sync(first.reference());

        AssetPackReference wrongHash =
                referenceWith(second.reference(), second.reference().size(), "0".repeat(64));
        AssetPackSyncException failure =
                catchThrowableOfType(
                        AssetPackSyncException.class,
                        () -> synchronizer(cache, Map.of(wrongHash.url(), second.archive())).sync(wrongHash));

        assertThat(failure.code()).isEqualTo(AssetPackSyncException.Code.ARCHIVE_HASH_MISMATCH);
        assertThat(firstSync.resolveOffline(first.reference())).isEqualTo(firstTree);

        Path secondTree =
                synchronizer(cache, Map.of(second.reference().url(), second.archive()))
                        .sync(second.reference());
        assertThat(Files.readString(secondTree.resolve("data/value.txt"))).isEqualTo("two");
        AssetPackSyncException stale =
                catchThrowableOfType(
                        AssetPackSyncException.class,
                        () -> firstSync.resolveOffline(first.reference()));
        assertThat(stale.code()).isEqualTo(AssetPackSyncException.Code.CACHE_STALE);
    }

    @Test
    void rejectsTruncationOversizeAndProviderFailure() throws Exception {
        PackFixture fixture = createPack("1.0.0", Map.of("data/value.txt", bytes("value")), "CC0-1.0");
        Path cache = temporaryDirectory.resolve("cache");

        AssetPackReference truncated =
                referenceWith(
                        fixture.reference(), fixture.reference().size() + 1L, fixture.reference().sha256());
        assertCode(
                AssetPackSyncException.Code.ARCHIVE_TRUNCATED,
                () -> synchronizer(cache, Map.of(truncated.url(), fixture.archive())).sync(truncated));

        AssetPackReference oversized =
                referenceWith(
                        fixture.reference(), fixture.reference().size() - 1L, fixture.reference().sha256());
        assertCode(
                AssetPackSyncException.Code.ARCHIVE_OVERSIZED,
                () -> synchronizer(cache, Map.of(oversized.url(), fixture.archive())).sync(oversized));

        AssetPackSynchronizer failedProvider =
                new AssetPackSynchronizer(
                        cache,
                        ignored -> {
                            throw new IOException("fixture provider failed");
                        });
        assertCode(
                AssetPackSyncException.Code.PROVIDER_FAILED,
                () -> failedProvider.sync(fixture.reference()));
    }

    @Test
    void rejectsTraversalAbsoluteSymlinkAndCompressionBombEntries() throws Exception {
        assertArchiveInvalid(createRawPack("1.0.0", Map.of("../escape.txt", bytes("escape"))));
        assertArchiveInvalid(createRawPack("1.0.1", Map.of("/absolute.txt", bytes("escape"))));

        PackFixture link = createRawPack("1.0.2", Map.of("links/current", bytes("target")));
        patchFirstCentralEntryAsUnixSymlink(link.archive());
        PackFixture patchedLink = withRecomputedArchiveLock(link);
        assertArchiveInvalid(patchedLink);

        byte[] compressible = new byte[32 * 1024];
        PackFixture bomb = createRawPack("1.0.3", Map.of("data/zeros.bin", compressible));
        AssetPackSynchronizer strict =
                new AssetPackSynchronizer(
                        temporaryDirectory.resolve("bomb-cache"),
                        new FileAssetPackProvider(Map.of(bomb.reference().url(), bomb.archive())),
                        new AssetPackSynchronizer.ArchiveLimits(
                                100, 64 * 1024, 128 * 1024, 2));
        assertCode(
                AssetPackSyncException.Code.ARCHIVE_INVALID,
                () -> strict.sync(bomb.reference()));
    }

    @Test
    void rejectsMissingOrphanAndForbiddenLicenseManifestContent() throws Exception {
        AssetPackManifest missingManifest =
                new AssetPackManifest(
                        "core",
                        "1.0.0",
                        "CC0-1.0",
                        List.of(new AssetPackFile("data/missing.txt", 7L, sha256(bytes("missing")))));
        PackFixture missing =
                createPackWithManifest("1.0.0", Map.of(), AssetPackManifestCodec.encode(missingManifest));
        assertManifestInvalid(missing);

        assertThatThrownBy(
                        () ->
                                new AssetPackManifest(
                                        "core", "1.0.1", "CC0-1.0", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file count");

        byte[] listed = bytes("listed");
        byte[] orphanBytes =
                bytes(
                        "{\"files\":[{\"path\":\"data/listed.txt\",\"sha256\":\""
                                + sha256(listed)
                                + "\",\"size\":6}],\"license\":\"CC0-1.0\",\"packId\":\"core\",\"version\":\"1.0.1\"}");
        PackFixture orphan =
                createPackWithManifest(
                        "1.0.1",
                        Map.of(
                                "data/listed.txt", listed,
                                "data/orphan.txt", bytes("orphan")),
                        orphanBytes);
        assertManifestInvalid(orphan);

        byte[] forbidden =
                bytes(
                        "{\"files\":[{\"path\":\"data/value.txt\",\"sha256\":\""
                                + sha256(bytes("value"))
                                + "\",\"size\":5}],\"license\":\"GPL-3.0-only\",\"packId\":\"core\",\"version\":\"1.0.2\"}");
        PackFixture forbiddenPack =
                createPackWithManifest(
                        "1.0.2", Map.of("data/value.txt", bytes("value")), forbidden);
        assertManifestInvalid(forbiddenPack);
    }

    @Test
    void detectsTamperingDuringOfflineRestart() throws Exception {
        PackFixture fixture = createPack("1.0.0", Map.of("data/value.txt", bytes("value")), "CC0-1.0");
        Path cache = temporaryDirectory.resolve("cache");
        AssetPackSynchronizer synchronizer =
                synchronizer(cache, Map.of(fixture.reference().url(), fixture.archive()));
        Path tree = synchronizer.sync(fixture.reference());
        Files.writeString(tree.resolve("data/value.txt"), "tampered");

        assertCode(
                AssetPackSyncException.Code.MANIFEST_INVALID,
                () -> synchronizer.resolveOffline(fixture.reference()));
    }

    private void assertArchiveInvalid(PackFixture fixture) throws Exception {
        assertCode(
                AssetPackSyncException.Code.ARCHIVE_INVALID,
                () ->
                        synchronizer(
                                        temporaryDirectory.resolve("invalid-" + fixture.reference().version()),
                                        Map.of(fixture.reference().url(), fixture.archive()))
                                .sync(fixture.reference()));
    }

    private void assertManifestInvalid(PackFixture fixture) throws Exception {
        assertCode(
                AssetPackSyncException.Code.MANIFEST_INVALID,
                () ->
                        synchronizer(
                                        temporaryDirectory.resolve("manifest-" + fixture.reference().version()),
                                        Map.of(fixture.reference().url(), fixture.archive()))
                                .sync(fixture.reference()));
    }

    private static void assertCode(
            AssetPackSyncException.Code expected, ThrowingOperation operation) {
        AssetPackSyncException failure =
                catchThrowableOfType(AssetPackSyncException.class, operation::run);
        assertThat(failure.code()).isEqualTo(expected);
    }

    private static AssetPackSynchronizer synchronizer(Path cache, Map<URI, Path> files) {
        return new AssetPackSynchronizer(cache, new FileAssetPackProvider(files));
    }

    private PackFixture createPack(String version, Map<String, byte[]> files, String license)
            throws Exception {
        List<AssetPackFile> entries = new ArrayList<>();
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                entries.add(
                                        new AssetPackFile(
                                                entry.getKey(),
                                                entry.getValue().length,
                                                sha256(entry.getValue()))));
        AssetPackManifest manifest = new AssetPackManifest("core", version, license, entries);
        return createPackWithManifest(version, files, AssetPackManifestCodec.encode(manifest));
    }

    private PackFixture createRawPack(String version, Map<String, byte[]> files) throws Exception {
        AssetPackManifest manifest =
                new AssetPackManifest(
                        "core",
                        version,
                        "CC0-1.0",
                        List.of(new AssetPackFile("placeholder.txt", 1L, sha256(bytes("x")))));
        return createPackWithManifest(version, files, AssetPackManifestCodec.encode(manifest));
    }

    private PackFixture createPackWithManifest(
            String version, Map<String, byte[]> files, byte[] manifestBytes) throws Exception {
        Path archive = temporaryDirectory.resolve("pack-" + version + '-' + System.nanoTime() + ".zip");
        Map<String, byte[]> allEntries = new LinkedHashMap<>();
        allEntries.put("manifest.json", manifestBytes);
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> allEntries.put(entry.getKey(), entry.getValue()));
        writeZip(archive, allEntries);
        byte[] archiveBytes = Files.readAllBytes(archive);
        String archiveHash = sha256(archiveBytes);
        URI uri =
                URI.create(
                        "https://assets.example.invalid/releases/core-"
                                + version
                                + '-'
                                + archiveHash.substring(0, 16)
                                + ".zip");
        AssetPackReference reference =
                new AssetPackReference(
                        "core",
                        version,
                        1,
                        uri,
                        archiveBytes.length,
                        archiveHash,
                        "manifest.json",
                        sha256(manifestBytes));
        return new PackFixture(reference, archive);
    }

    private static void writeZip(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            entries.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(
                            entry -> {
                                try {
                                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                                    zipEntry.setTime(0L);
                                    output.putNextEntry(zipEntry);
                                    output.write(entry.getValue());
                                    output.closeEntry();
                                } catch (IOException exception) {
                                    throw new ZipWriteException(exception);
                                }
                            });
        } catch (ZipWriteException exception) {
            throw exception.ioException();
        }
    }

    private static void patchFirstCentralEntryAsUnixSymlink(Path archive) throws IOException {
        byte[] bytes = Files.readAllBytes(archive);
        int central = findSignature(bytes, 0x02014b50);
        if (central < 0) {
            throw new IOException("test ZIP has no central entry");
        }
        bytes[central + 5] = 3;
        int external = 0120777 << 16;
        for (int index = 0; index < 4; index++) {
            bytes[central + 38 + index] = (byte) (external >>> (index * 8));
        }
        Files.write(archive, bytes);
    }

    private static int findSignature(byte[] bytes, int signature) {
        for (int index = 0; index <= bytes.length - 4; index++) {
            int candidate =
                    (bytes[index] & 0xff)
                            | ((bytes[index + 1] & 0xff) << 8)
                            | ((bytes[index + 2] & 0xff) << 16)
                            | ((bytes[index + 3] & 0xff) << 24);
            if (candidate == signature) {
                return index;
            }
        }
        return -1;
    }

    private static PackFixture withRecomputedArchiveLock(PackFixture fixture) throws IOException {
        byte[] archiveBytes = Files.readAllBytes(fixture.archive());
        AssetPackReference old = fixture.reference();
        AssetPackReference updated =
                new AssetPackReference(
                        old.id(),
                        old.version(),
                        old.formatVersion(),
                        old.url(),
                        archiveBytes.length,
                        sha256(archiveBytes),
                        old.manifestPath(),
                        old.manifestSha256());
        return new PackFixture(updated, fixture.archive());
    }

    private static AssetPackReference referenceWith(
            AssetPackReference source, long size, String sha256) {
        return new AssetPackReference(
                source.id(),
                source.version(),
                source.formatVersion(),
                source.url(),
                size,
                sha256,
                source.manifestPath(),
                source.manifestSha256());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record PackFixture(AssetPackReference reference, Path archive) {}

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private static final class ZipWriteException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ZipWriteException(IOException cause) {
            super(cause);
        }

        private IOException ioException() {
            return (IOException) getCause();
        }
    }
}
