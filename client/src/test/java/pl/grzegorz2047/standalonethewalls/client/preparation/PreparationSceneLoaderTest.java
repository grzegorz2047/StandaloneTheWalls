package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationSceneLoaderTest {
    private static final byte[] MAP_SHA256 =
            HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);

    @Test
    void loadsTheVerifiedSceneForTheAuthoritativeSpawn() throws PreparationSceneLoadException {
        VerifiedPreparationScene scene = PreparationSceneLoader.loadDefault(greenSpawn());

        assertThat(scene.mapId()).isEqualTo(MinimalPreparationBundle.MAP_ID);
        assertThat(scene.mapSha256()).containsExactly(MAP_SHA256);
        assertThat(scene.region().team()).isEqualTo(PreparationTeam.GREEN);
        assertThat(scene.region().contains(scene.spawn().position())).isTrue();
        assertThat(scene.spawn().index()).isZero();
        assertThat(scene.spawn().position().x()).isEqualTo(-15.0d);
        assertThat(scene.spawn().position().y()).isEqualTo(0.5d);
        assertThat(scene.spawn().position().z()).isEqualTo(-14.0d);
        assertThat(scene.spawn().yawDegrees()).isEqualTo(45.0d);
        assertThat(scene.sceneDocument().nodeCount()).isPositive();
        assertThat(scene.sceneDocument().meshCount()).isPositive();
        assertThat(scene.sceneDocument().lightCount()).isPositive();
        assertThat(scene.collisionDocument().nodeCount()).isPositive();
        assertThat(scene.collisionDocument().meshCount()).isPositive();
        assertThat(scene.sceneGlb()).isNotEmpty();
        assertThat(scene.collisionGlb()).isNotEmpty();
    }

    @Test
    void returnedBinaryStateIsDefensivelyCopied() throws PreparationSceneLoadException {
        VerifiedPreparationScene scene = PreparationSceneLoader.loadDefault(greenSpawn());
        byte[] digest = scene.mapSha256();
        byte[] sceneGlb = scene.sceneGlb();
        byte[] collisionGlb = scene.collisionGlb();

        digest[0] ^= 0x7f;
        sceneGlb[0] ^= 0x7f;
        collisionGlb[0] ^= 0x7f;

        assertThat(scene.mapSha256()).containsExactly(MAP_SHA256);
        assertThat(scene.sceneGlb()[0]).isNotEqualTo(sceneGlb[0]);
        assertThat(scene.collisionGlb()[0]).isNotEqualTo(collisionGlb[0]);
    }

    @Test
    void rejectsAReportedMapIdThatDoesNotMatchTheVerifiedBundle() {
        PreparationSpawnAssignment assignment =
                assignment(
                        "other_map", MAP_SHA256, LobbyTeam.GREEN, 0, -15.0d, 0.5d, -14.0d, 45.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.MAP_ID_MISMATCH);
    }

    @Test
    void rejectsAReportedDigestThatDoesNotMatchTheVerifiedArchive() {
        byte[] wrongDigest = MAP_SHA256.clone();
        wrongDigest[0] ^= 0x01;
        PreparationSpawnAssignment assignment =
                assignment(
                        MinimalPreparationBundle.MAP_ID,
                        wrongDigest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.MAP_SHA256_MISMATCH);
    }

    @Test
    void rejectsAnUnknownSpawnIndex() {
        PreparationSpawnAssignment assignment =
                assignment(
                        MinimalPreparationBundle.MAP_ID,
                        MAP_SHA256,
                        LobbyTeam.GREEN,
                        4_095,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.SPAWN_MISSING);
    }

    @Test
    void rejectsAReportedTeamThatDoesNotOwnTheVerifiedSpawn() {
        PreparationSpawnAssignment assignment =
                assignment(
                        MinimalPreparationBundle.MAP_ID,
                        MAP_SHA256,
                        LobbyTeam.BLUE,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH);
    }

    @Test
    void rejectsCoordinatesThatDoNotMatchTheVerifiedSpawn() {
        PreparationSpawnAssignment assignment =
                assignment(
                        MinimalPreparationBundle.MAP_ID,
                        MAP_SHA256,
                        LobbyTeam.GREEN,
                        0,
                        -14.0d,
                        0.5d,
                        -14.0d,
                        45.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH);
    }

    @Test
    void rejectsYawThatDoesNotMatchTheVerifiedSpawn() {
        PreparationSpawnAssignment assignment =
                assignment(
                        MinimalPreparationBundle.MAP_ID,
                        MAP_SHA256,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        44.0d);

        assertFailure(assignment, PreparationSceneLoadException.Code.SPAWN_STATE_MISMATCH);
    }

    private static void assertFailure(
            PreparationSpawnAssignment assignment, PreparationSceneLoadException.Code code) {
        try {
            PreparationSceneLoader.loadDefault(assignment);
            throw new AssertionError("preparation scene load unexpectedly succeeded");
        } catch (PreparationSceneLoadException failure) {
            assertThat(failure.code()).isEqualTo(code);
        }
    }

    private static PreparationSpawnAssignment greenSpawn() {
        return assignment(
                MinimalPreparationBundle.MAP_ID,
                MAP_SHA256,
                LobbyTeam.GREEN,
                0,
                -15.0d,
                0.5d,
                -14.0d,
                45.0d);
    }

    private static PreparationSpawnAssignment assignment(
            String mapId,
            byte[] digest,
            LobbyTeam team,
            int spawnIndex,
            double x,
            double y,
            double z,
            double yawDegrees) {
        return new PreparationSpawnAssignment(
                8L, 1L, mapId, digest, team, spawnIndex, x, y, z, yawDegrees);
    }
}
