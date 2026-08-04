package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationGameplayJsonTest {
    @Test
    void decodesACompleteFourTeamPreparationLayout() throws PreparationGameplayException {
        PreparationGameplay gameplay = PreparationGameplayJson.decode(validJson());

        assertThat(gameplay.schemaVersion()).isEqualTo(PreparationGameplay.SCHEMA_VERSION);
        assertThat(gameplay.regions())
                .extracting(PreparationRegion::team)
                .containsExactly(
                        PreparationTeam.GREEN,
                        PreparationTeam.BLUE,
                        PreparationTeam.RED,
                        PreparationTeam.YELLOW);
        assertThat(gameplay.spawns())
                .extracting(PreparationMapSpawn::index)
                .containsExactly(2, 8, 3, 9)
                .doesNotHaveDuplicates();
        assertThat(gameplay.spawns())
                .allSatisfy(
                        spawn ->
                                assertThat(
                                                gameplay.regions().stream()
                                                        .filter(
                                                                region ->
                                                                        region.team()
                                                                                == spawn.team())
                                                        .findFirst()
                                                        .orElseThrow()
                                                        .contains(spawn.position()))
                                        .isTrue());
    }

    @Test
    void rejectsUnknownDuplicateMissingAndTrailingData() {
        assertCode(
                validText().replace("\"schema\":1", "\"schema\":1,\"unknown\":0"),
                PreparationGameplayException.Code.UNKNOWN_FIELD);
        assertCode(
                validText().replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
                PreparationGameplayException.Code.MALFORMED_JSON);
        assertCode(
                validText().replace("\"schema\":1,", ""),
                PreparationGameplayException.Code.MISSING_FIELD);
        assertCode(validText() + "{}", PreparationGameplayException.Code.MALFORMED_JSON);
    }

    @Test
    void rejectsUnsupportedSchemaInvalidTeamAndInvalidVector() {
        assertCode(
                validText().replace("\"schema\":1", "\"schema\":2"),
                PreparationGameplayException.Code.UNSUPPORTED_SCHEMA);
        assertCode(
                validText().replaceFirst("\"GREEN\"", "\"green\""),
                PreparationGameplayException.Code.INVALID_TEAM);
        assertCode(
                validText().replaceFirst("\[-20,-1,-20\]", "[-20,-1]"),
                PreparationGameplayException.Code.INVALID_VALUE);
    }

    @Test
    void rejectsOverlappingRegionsDuplicateIndicesAndSpawnOutsideItsRegion() {
        String overlapping =
                validText().replace(
                                "\"minimum\":[1,-1,-20]",
                                "\"minimum\":[-10,-1,-20]");
        assertCode(overlapping, PreparationGameplayException.Code.INVALID_LAYOUT);

        String duplicateIndex = validText().replace("\"index\":8", "\"index\":2");
        assertCode(duplicateIndex, PreparationGameplayException.Code.INVALID_LAYOUT);

        String outside = validText().replace("[10,2,-10]", "[-10,2,-10]");
        assertCode(outside, PreparationGameplayException.Code.INVALID_LAYOUT);
    }

    @Test
    void rejectsMissingTeamSpawnAndUnsupportedRegionCount() {
        String missingYellowSpawn =
                validText().replace(
                        ",{\"index\":9,\"position\":[10,2,10],\"team\":\"YELLOW\",\"yaw\":-180}",
                        "");
        assertCode(missingYellowSpawn, PreparationGameplayException.Code.INVALID_LAYOUT);

        String threeRegions =
                validText().replace(
                        ",{\"maximum\":[20,20,20],\"minimum\":[1,-1,1],\"team\":\"YELLOW\"}",
                        "");
        assertCode(threeRegions, PreparationGameplayException.Code.INVALID_LAYOUT);
    }

    @Test
    void rejectsNullEmptyAndOversizedPayloads() {
        assertCode((byte[]) null, PreparationGameplayException.Code.INVALID_SIZE);
        assertCode(new byte[0], PreparationGameplayException.Code.INVALID_SIZE);
        assertCode(
                new byte[PreparationGameplayJson.MAXIMUM_BYTES + 1],
                PreparationGameplayException.Code.INVALID_SIZE);
    }

    @Test
    void typedLayoutCopiesCollectionsAndAllowsTouchingBoundaries() {
        PreparationRegion green =
                new PreparationRegion(
                        PreparationTeam.GREEN,
                        new MapVector3(-10.0d, 0.0d, -10.0d),
                        new MapVector3(0.0d, 10.0d, 10.0d));
        PreparationRegion blue =
                new PreparationRegion(
                        PreparationTeam.BLUE,
                        new MapVector3(0.0d, 0.0d, -10.0d),
                        new MapVector3(10.0d, 10.0d, 10.0d));
        PreparationGameplay gameplay =
                new PreparationGameplay(
                        1,
                        List.of(green, blue),
                        List.of(
                                new PreparationMapSpawn(
                                        1,
                                        PreparationTeam.GREEN,
                                        new MapVector3(-5.0d, 1.0d, 0.0d),
                                        0.0d),
                                new PreparationMapSpawn(
                                        2,
                                        PreparationTeam.BLUE,
                                        new MapVector3(5.0d, 1.0d, 0.0d),
                                        0.0d)));

        assertThat(gameplay.regions()).containsExactly(green, blue);
        assertThatThrownBy(() -> gameplay.regions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(green.overlapsVolume(blue)).isFalse();
    }

    private static byte[] validJson() {
        return validText().getBytes(StandardCharsets.UTF_8);
    }

    private static String validText() {
        return """
                {"regions":[{"maximum":[-1,20,-1],"minimum":[-20,-1,-20],"team":"GREEN"},{"maximum":[20,20,-1],"minimum":[1,-1,-20],"team":"BLUE"},{"maximum":[-1,20,20],"minimum":[-20,-1,1],"team":"RED"},{"maximum":[20,20,20],"minimum":[1,-1,1],"team":"YELLOW"}],"schema":1,"spawns":[{"index":2,"position":[-10,2,-10],"team":"GREEN","yaw":0},{"index":8,"position":[10,2,-10],"team":"BLUE","yaw":90},{"index":3,"position":[-10,2,10],"team":"RED","yaw":-90},{"index":9,"position":[10,2,10],"team":"YELLOW","yaw":-180}]}
                """;
    }

    private static void assertCode(String json, PreparationGameplayException.Code expected) {
        assertCode(json == null ? null : json.getBytes(StandardCharsets.UTF_8), expected);
    }

    private static void assertCode(byte[] json, PreparationGameplayException.Code expected) {
        assertThatThrownBy(() -> PreparationGameplayJson.decode(json))
                .isInstanceOfSatisfying(
                        PreparationGameplayException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }
}
