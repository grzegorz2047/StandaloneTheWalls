package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapManifestValidatorTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void validatesAndTypesAMinimalFourTeamManifest() {
        MapManifestValidation validation = MapManifestValidator.validate(validDraft());

        assertThat(validation.isValid()).isTrue();
        assertThat(validation.issues()).isEmpty();
        MapManifest manifest = validation.manifest().orElseThrow();
        assertThat(manifest.id()).isEqualTo("citadel_divide");
        assertThat(manifest.version()).isEqualTo(new SemanticVersion(1, 2, 3, "rc.1", "build.7"));
        assertThat(manifest.maximumPlayers()).isEqualTo(40);
        assertThat(manifest.teamCount()).isEqualTo(4);
        assertThat(manifest.files()).containsKey("scene.glb");
        assertThat(manifest.files().get("scene.glb").value()).isEqualTo(HASH);
    }

    @Test
    void reportsMissingFieldsWithStablePathsInsteadOfThrowing() {
        MapManifestValidation validation = MapManifestValidator.validate(new MapManifestDraft(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.issues())
                .extracting(MapValidationIssue::path)
                .contains(
                        "$.schemaVersion",
                        "$.id",
                        "$.name",
                        "$.author",
                        "$.version",
                        "$.minimumPlayers",
                        "$.maximumPlayers",
                        "$.teamCount",
                        "$.playersPerTeam",
                        "$.requiredProtocol.major",
                        "$.requiredProtocol.minor",
                        "$.license",
                        "$.files",
                        "$.limits");
    }

    @Test
    void rejectsUnsupportedTeamLayoutsAndConflictingCapacity() {
        MapManifestDraft base = validDraft();
        MapManifestDraft invalid = copy(base, 3, 10, 2, 40);

        MapManifestValidation validation = MapManifestValidator.validate(invalid);

        assertThat(validation.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.path()).isEqualTo("$.teamCount");
                    assertThat(issue.code()).isEqualTo(MapValidationIssue.Code.UNSUPPORTED);
                })
                .anySatisfy(issue -> {
                    assertThat(issue.path()).isEqualTo("$.maximumPlayers");
                    assertThat(issue.code()).isEqualTo(MapValidationIssue.Code.CONFLICT);
                });
    }

    @Test
    void rejectsUnsafePathsBadHashesAndMissingRequiredMembers() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("../scene.glb", HASH);
        files.put("collision.glb", "ABC");
        files.put("gameplay.json", HASH);
        files.put("thumbnail.webp", HASH);
        files.put("licenses.json", HASH);
        MapManifestDraft base = validDraft();
        MapManifestDraft invalid = withFiles(base, files);

        MapManifestValidation validation = MapManifestValidator.validate(invalid);

        assertThat(validation.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.path()).isEqualTo("$.files[../scene.glb]");
                    assertThat(issue.code()).isEqualTo(MapValidationIssue.Code.UNSAFE_PATH);
                })
                .anySatisfy(issue -> {
                    assertThat(issue.path()).isEqualTo("$.files[collision.glb]");
                    assertThat(issue.code()).isEqualTo(MapValidationIssue.Code.FORMAT);
                })
                .anySatisfy(issue -> {
                    assertThat(issue.path()).isEqualTo("$.files[scene.glb]");
                    assertThat(issue.code()).isEqualTo(MapValidationIssue.Code.REQUIRED);
                });
    }

    @Test
    void rejectsZipBombShapedBudgetsAndNonPowerOfTwoTextures() {
        MapManifestDraft base = validDraft();
        MapManifestDraft invalid = withLimits(
                base,
                new MapLimitsDraft(
                        MapManifestValidator.MAXIMUM_ARCHIVE_BYTES + 1,
                        100L,
                        1,
                        MapManifestValidator.MAXIMUM_SCENE_NODES + 1,
                        MapManifestValidator.MAXIMUM_TRIANGLES + 1,
                        3000));

        MapManifestValidation validation = MapManifestValidator.validate(invalid);

        assertThat(validation.issues())
                .extracting(MapValidationIssue::path)
                .contains(
                        "$.limits.archiveBytes",
                        "$.limits.uncompressedBytes",
                        "$.limits.fileCount",
                        "$.limits.sceneNodes",
                        "$.limits.triangles",
                        "$.limits.textureDimension");
    }

    @Test
    void requiresCanonicalSemanticVersionAndPortableText() {
        MapManifestDraft base = validDraft();
        MapManifestDraft invalid = new MapManifestDraft(
                base.schemaVersion(),
                "Uppercase ID",
                " name ",
                "author\u202Ehidden",
                "01.2.3",
                base.minimumPlayers(),
                base.maximumPlayers(),
                base.teamCount(),
                base.playersPerTeam(),
                base.requiredProtocolMajor(),
                base.requiredProtocolMinor(),
                "not a license expression",
                base.files(),
                base.limits());

        MapManifestValidation validation = MapManifestValidator.validate(invalid);

        assertThat(validation.issues())
                .extracting(MapValidationIssue::path)
                .contains("$.id", "$.name", "$.author", "$.version", "$.license");
    }

    @Test
    void copiesInputAndValidatedFileMaps() {
        LinkedHashMap<String, String> mutableFiles = validFiles();
        MapManifestDraft draft = withFiles(validDraft(), mutableFiles);
        mutableFiles.put("injected.bin", HASH);

        MapManifest manifest = MapManifestValidator.validate(draft).manifest().orElseThrow();

        assertThat(draft.files()).doesNotContainKey("injected.bin");
        assertThat(manifest.files()).doesNotContainKey("injected.bin");
    }

    private static MapManifestDraft validDraft() {
        return new MapManifestDraft(
                1,
                "citadel_divide",
                "Citadel Divide",
                "Sunderfront Team",
                "1.2.3-rc.1+build.7",
                4,
                40,
                4,
                10,
                1,
                0,
                "CC0-1.0",
                validFiles(),
                new MapLimitsDraft(
                        50L * 1024L * 1024L,
                        120L * 1024L * 1024L,
                        32,
                        20_000,
                        500_000,
                        2048));
    }

    private static LinkedHashMap<String, String> validFiles() {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("scene.glb", HASH);
        files.put("collision.glb", HASH);
        files.put("gameplay.json", HASH);
        files.put("thumbnail.webp", HASH);
        files.put("licenses.json", HASH);
        return files;
    }

    private static MapManifestDraft withFiles(MapManifestDraft base, Map<String, String> files) {
        return new MapManifestDraft(
                base.schemaVersion(),
                base.id(),
                base.name(),
                base.author(),
                base.version(),
                base.minimumPlayers(),
                base.maximumPlayers(),
                base.teamCount(),
                base.playersPerTeam(),
                base.requiredProtocolMajor(),
                base.requiredProtocolMinor(),
                base.license(),
                files,
                base.limits());
    }

    private static MapManifestDraft withLimits(MapManifestDraft base, MapLimitsDraft limits) {
        return new MapManifestDraft(
                base.schemaVersion(),
                base.id(),
                base.name(),
                base.author(),
                base.version(),
                base.minimumPlayers(),
                base.maximumPlayers(),
                base.teamCount(),
                base.playersPerTeam(),
                base.requiredProtocolMajor(),
                base.requiredProtocolMinor(),
                base.license(),
                base.files(),
                limits);
    }

    private static MapManifestDraft copy(
            MapManifestDraft base,
            int teamCount,
            int playersPerTeam,
            int minimumPlayers,
            int maximumPlayers) {
        return new MapManifestDraft(
                base.schemaVersion(),
                base.id(),
                base.name(),
                base.author(),
                base.version(),
                minimumPlayers,
                maximumPlayers,
                teamCount,
                playersPerTeam,
                base.requiredProtocolMajor(),
                base.requiredProtocolMinor(),
                base.license(),
                base.files(),
                base.limits());
    }
}
