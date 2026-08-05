package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationMovementControllerTest {
    @Test
    void movesForwardAndRightUsingYawRegardlessOfCameraPitch()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player().rotateView(0.0d, 60.0d);
        PreparationCollisionWorld collisions = collisions(player);

        PreparationPlayerState forward =
                PreparationMovementController.move(player, collisions, 1.0d, 0.0d, 0.1d);
        PreparationPlayerState right =
                PreparationMovementController.move(player, collisions, 0.0d, 1.0d, 0.1d);

        assertThat(forward.position().x()).isCloseTo(-14.6464466d, within(0.000001d));
        assertThat(forward.position().z()).isCloseTo(-13.6464466d, within(0.000001d));
        assertThat(forward.pitchDegrees()).isEqualTo(60.0d);
        assertThat(right.position().x()).isCloseTo(-15.3535534d, within(0.000001d));
        assertThat(right.position().z()).isCloseTo(-13.6464466d, within(0.000001d));
        assertThat(right.pitchDegrees()).isEqualTo(60.0d);
    }

    @Test
    void normalizesDiagonalInputAndCapsLongFrames()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player();
        PreparationCollisionWorld collisions = collisions(player);

        PreparationPlayerState diagonal =
                PreparationMovementController.move(player, collisions, 1.0d, 1.0d, 0.1d);
        PreparationPlayerState longFrame =
                PreparationMovementController.move(player, collisions, 1.0d, 0.0d, 10.0d);

        assertThat(diagonal.position().x()).isCloseTo(-15.0d, within(0.000001d));
        assertThat(diagonal.position().z()).isCloseTo(-13.5d, within(0.000001d));
        assertThat(distance(player.position(), diagonal.position()))
                .isCloseTo(0.5d, within(0.000001d));
        assertThat(longFrame.position())
                .isEqualTo(
                        PreparationMovementController.move(player, collisions, 1.0d, 0.0d, 0.1d)
                                .position());
    }

    @Test
    void preservesTheRegionBoundaryAndReturnsTheSameStateWhenBlocked()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState boundary = player().moveHorizontal(-100.0d, -100.0d);
        PreparationCollisionWorld collisions = collisions(boundary);

        PreparationPlayerState blocked =
                PreparationMovementController.move(boundary, collisions, -1.0d, 0.0d, 0.1d);

        assertThat(blocked).isSameAs(boundary);
        assertThat(blocked.position()).isEqualTo(new MapVector3(-18.0d, 0.5d, -18.0d));
    }

    @Test
    void rotatesYawAndPitchFromBoundedMouseDelta() throws PreparationSceneLoadException {
        PreparationPlayerState player = player();

        PreparationPlayerState rotated =
                PreparationMovementController.rotate(player, 100.0d, 50.0d);

        assertThat(rotated.yawDegrees()).isEqualTo(57.0d);
        assertThat(rotated.pitchDegrees()).isEqualTo(5.0d);
        assertThat(rotated.position()).isEqualTo(player.position());
        assertThat(PreparationMovementController.rotate(player, 0.0d, 0.0d)).isSameAs(player);
    }

    @Test
    void sprintsAtDeterministicSpeedAndStillNormalizesDiagonalInput()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player();
        PreparationCollisionWorld collisions = collisions(player);

        PreparationPlayerState walking =
                PreparationMovementController.move(player, collisions, 1.0d, 0.0d, false, 0.1d);
        PreparationPlayerState sprinting =
                PreparationMovementController.move(player, collisions, 1.0d, 0.0d, true, 0.1d);
        PreparationPlayerState diagonalSprint =
                PreparationMovementController.move(player, collisions, 1.0d, 1.0d, true, 0.1d);

        assertThat(distance(player.position(), walking.position()))
                .isCloseTo(0.5d, within(0.000001d));
        assertThat(distance(player.position(), sprinting.position()))
                .isCloseTo(0.8d, within(0.000001d));
        assertThat(distance(player.position(), diagonalSprint.position()))
                .isCloseTo(0.8d, within(0.000001d));
    }

    @Test
    void rejectsInvalidAxesTimeAndMouseDelta()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState player = player();
        PreparationCollisionWorld collisions = collisions(player);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(
                                        PreparationMovementController.move(
                                                player, collisions, 1.1d, 0.0d, 0.1d))
                                .isSameAs(player));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(
                                        PreparationMovementController.move(
                                                player,
                                                collisions,
                                                0.0d,
                                                0.0d,
                                                Double.POSITIVE_INFINITY))
                                .isSameAs(player));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(PreparationMovementController.rotate(player, Double.NaN, 0.0d))
                                .isSameAs(player));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(PreparationMovementController.rotate(player, 0.0d, Double.NaN))
                                .isSameAs(player));
    }

    private static PreparationPlayerState player() throws PreparationSceneLoadException {
        return PreparationPlayerState.atAuthoritativeSpawn(verifiedScene());
    }

    private static PreparationCollisionWorld collisions(PreparationPlayerState player)
            throws PreparationSceneGraphException {
        return PreparationCollisionWorld.load(new DesktopAssetManager(true), player.scene());
    }

    private static VerifiedPreparationScene verifiedScene() throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        return PreparationSceneLoader.loadDefault(assignment);
    }

    private static double distance(MapVector3 first, MapVector3 second) {
        return Math.hypot(second.x() - first.x(), second.z() - first.z());
    }
}
