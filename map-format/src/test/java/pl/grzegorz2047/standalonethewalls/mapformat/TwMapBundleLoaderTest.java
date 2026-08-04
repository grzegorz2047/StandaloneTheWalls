package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TwMapBundleLoaderTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void loadsOnlyACompleteHashVerifiedBundle() throws IOException, TwMapBundleException {
        Map<String, byte[]> members = validMembers();
        byte[] archive = archive(manifest(members), members);

        VerifiedMapBundle bundle = TwMapBundleLoader.load(archive, POLICY);

        assertThat(bundle.manifest().id()).isEqualTo("minimal_preparation");
        assertThat(bundle.gameplay().regions()).hasSize(4);
        assertThat(bundle.gameplay().spawns()).hasSize(4);
        assertThat(bundle.memberNames()).containsExactlyInAnyOrderElementsOf(members.keySet());
        assertThat(bundle.archiveSha256().value()).hasSize(64);
        assertThat(bundle.member("scene.glb")).isEqualTo(members.get("scene.glb"));
    }

    @Test
    void defensivelyCopiesArchiveInputAndEveryExposedMember()
            throws IOException, TwMapBundleException {
        Map<String, byte[]> members = validMembers();
        byte[] archive = archive(manifest(members), members);
        VerifiedMapBundle bundle = TwMapBundleLoader.load(archive, POLICY);

        archive[0] = 0;
        byte[] scene = bundle.member("scene.glb");
        scene[0] = 0;
        byte[] manifestJson = bundle.manifestJson();
        manifestJson[0] = 0;

        assertThat(bundle.member("scene.glb")).isEqualTo(members.get("scene.glb"));
        assertThat(bundle.manifestJson()[0]).isEqualTo((byte) '{');
        assertThatThrownBy(() -> bundle.memberNames().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsWrongDigestMissingAndUndeclaredMembers() throws IOException {
        Map<String, byte[]> declared = validMembers();
        Map<String, byte[]> tampered = copyMembers(declared);
        tampered.put("scene.glb", "tampered".getBytes(StandardCharsets.UTF_8));
        assertCode(
                archive(manifest(declared), tampered),
                POLICY,
                TwMapBundleException.Code.HASH_MISMATCH);

        Map<String, byte[]> missing = copyMembers(declared);
        missing.remove("collision.glb");
        assertCode(
                archive(manifest(declared), missing),
                POLICY,
                TwMapBundleException.Code.MISSING_ENTRY);

        Map<String, byte[]> extra = copyMembers(declared);
        extra.put("extra.bin", new byte[] {1});
        assertCode(
                archive(manifest(declared), extra),
                POLICY,
                TwMapBundleException.Code.UNDECLARED_ENTRY);
    }

    @Test
    void rejectsUnsafeEntriesAndMissingManifest() throws IOException {
        Map<String, byte[]> members = validMembers();
        Map<String, byte[]> unsafe = copyMembers(members);
        unsafe.put("../escape.bin", new byte[] {1});
        assertCode(
                archive(manifest(members), unsafe),
                POLICY,
                TwMapBundleException.Code.UNSAFE_ENTRY);
        assertCode(
                archive(null, members),
                POLICY,
                TwMapBundleException.Code.MISSING_MANIFEST);
    }

    @Test
    void rejectsInvalidManifestJsonAndSemanticManifest() throws IOException {
        Map<String, byte[]> members = validMembers();
        String validManifest = manifest(members);
        assertCode(
                archive(
                        validManifest.replace(
                                "\"schemaVersion\":1",
                                "\"schemaVersion\":1,\"unknown\":0"),
                        members),
                POLICY,
                TwMapBundleException.Code.INVALID_MANIFEST_JSON);

        byte[] semanticallyInvalid =
                archive(validManifest.replace("\"teamCount\":4", "\"teamCount\":3"), members);
        assertThatThrownBy(() -> TwMapBundleLoader.load(semanticallyInvalid, POLICY))
                .isInstanceOfSatisfying(
                        TwMapBundleException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(TwMapBundleException.Code.INVALID_MANIFEST);
                            assertThat(exception.manifestIssues()).isNotEmpty();
                        });
    }

    @Test
    void rejectsGameplayThatMatchesItsDigestButFailsDomainValidation() throws IOException {
        Map<String, byte[]> members = validMembers();
        members.put("gameplay.json", "{}".getBytes(StandardCharsets.UTF_8));

        assertCode(
                archive(manifest(members), members),
                POLICY,
                TwMapBundleException.Code.INVALID_GAMEPLAY);
    }

    @Test
    void enforcesLocalArchiveAndExpansionLimits() throws IOException {
        Map<String, byte[]> members = validMembers();
        byte[] archive = archive(manifest(members), members);
        TwMapLoadPolicy archiveTooSmall =
                new TwMapLoadPolicy(archive.length - 1, 4 * 1024 * 1024, 16, 100);
        assertCode(
                archive,
                archiveTooSmall,
                TwMapBundleException.Code.INVALID_ARCHIVE_SIZE);

        Map<String, byte[]> compressible = validMembers();
        compressible.put("scene.glb", new byte[128 * 1024]);
        byte[] compressedArchive = archive(manifest(compressible), compressible);
        TwMapLoadPolicy ratioOne =
                new TwMapLoadPolicy(2 * 1024 * 1024, 2 * 1024 * 1024, 16, 1);
        assertCode(
                compressedArchive,
                ratioOne,
                TwMapBundleException.Code.EXPANSION_LIMIT);
    }

    @Test
    void rejectsNonZipAndArchiveBudgetsAboveLocalPolicy() throws IOException {
        assertCode(
                "not-a-zip".getBytes(StandardCharsets.UTF_8),
                POLICY,
                TwMapBundleException.Code.MALFORMED_ARCHIVE);

        Map<String, byte[]> members = validMembers();
        String oversizedBudget =
                manifest(members)
                        .replace(
                                "\"archiveBytes\":1048576",
                                "\"archiveBytes\":3145728")
                        .replace(
                                "\"uncompressedBytes\":2097152",
                                "\"uncompressedBytes\":4194304");
        assertCode(
                archive(oversizedBudget, members),
                POLICY,
                TwMapBundleException.Code.INVALID_MANIFEST);
    }

    private static void assertCode(
            byte[] archive, TwMapLoadPolicy policy, TwMapBundleException.Code expected) {
        assertThatThrownBy(() -> TwMapBundleLoader.load(archive, policy))
                .isInstanceOfSatisfying(
                        TwMapBundleException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static Map<String, byte[]> validMembers() {
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("scene.glb", "scene-v1".getBytes(StandardCharsets.UTF_8));
        members.put("collision.glb", "collision-v1".getBytes(StandardCharsets.UTF_8));
        members.put("gameplay.json", gameplayJson().getBytes(StandardCharsets.UTF_8));
        members.put("thumbnail.webp", "webp-v1".getBytes(StandardCharsets.UTF_8));
        members.put("licenses.json", "{\"license\":\"CC0-1.0\"}".getBytes(StandardCharsets.UTF_8));
        return members;
    }

    private static Map<String, byte[]> copyMembers(Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        return copy;
    }

    private static byte[] archive(String manifest, Map<String, byte[]> members)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            if (manifest != null) {
                writeEntry(zip, TwMapBundleLoader.MANIFEST_PATH, manifest.getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<String, byte[]> member : members.entrySet()) {
                writeEntry(zip, member.getKey(), member.getValue());
            }
        }
        return output.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String path, byte[] bytes)
            throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String manifest(Map<String, byte[]> members) {
        StringBuilder files = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, byte[]> member : members.entrySet()) {
            if (!first) {
                files.append(',');
            }
            first = false;
            files.append('"')
                    .append(member.getKey())
                    .append("\":\"")
                    .append(sha256(member.getValue()))
                    .append('"');
        }
        return """
                {"author":"Sunderfront Team","files":{%s},"id":"minimal_preparation","license":"CC0-1.0","limits":{"archiveBytes":1048576,"fileCount":%d,"sceneNodes":100,"textureDimension":256,"triangles":1000,"uncompressedBytes":2097152},"maximumPlayers":40,"minimumPlayers":4,"name":"Minimal Preparation","playersPerTeam":10,"requiredProtocol":{"major":1,"minor":0},"schemaVersion":1,"teamCount":4,"version":"1.0.0"}
                """
                .formatted(files, members.size());
    }

    private static String gameplayJson() {
        return """
                {"regions":[{"maximum":[-1,20,-1],"minimum":[-20,-1,-20],"team":"GREEN"},{"maximum":[20,20,-1],"minimum":[1,-1,-20],"team":"BLUE"},{"maximum":[-1,20,20],"minimum":[-20,-1,1],"team":"RED"},{"maximum":[20,20,20],"minimum":[1,-1,1],"team":"YELLOW"}],"schema":1,"spawns":[{"index":2,"position":[-10,2,-10],"team":"GREEN","yaw":0},{"index":8,"position":[10,2,-10],"team":"BLUE","yaw":90},{"index":3,"position":[-10,2,10],"team":"RED","yaw":-90},{"index":9,"position":[10,2,10],"team":"YELLOW","yaw":-180}]}
                """;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(Character.forDigit((current >>> 4) & 0x0F, 16));
                value.append(Character.forDigit(current & 0x0F, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
