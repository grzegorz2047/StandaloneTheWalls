package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jme3.renderer.Camera;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationCameraPlacementTest {
    @Test
    void crouchingLowersOnlyTheLocalCameraByTheBoundedOffset()
            throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedScene());
        Camera camera = new Camera(1_280, 720);

        PreparationCameraPlacement.apply(camera, player, false);
        float standingY = camera.getLocation().y;
        float standingX = camera.getLocation().x;
        float standingZ = camera.getLocation().z;
        var standingDirection = camera.getDirection().clone();

        PreparationCameraPlacement.apply(camera, player, true);

        assertThat(camera.getLocation().x).isEqualTo(standingX);
        assertThat(camera.getLocation().z).isEqualTo(standingZ);
        assertThat((double) standingY - camera.getLocation().y)
                .isCloseTo(
                        PreparationCameraPlacement.CROUCHING_CAMERA_DROP_METRES, within(0.000001d));
        assertThat(camera.getDirection()).isEqualTo(standingDirection);
    }

    @Test
    void acceptedCrouchKeepsTheCameraLoweredAfterTheInputIsReleased()
            throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedScene()).withCrouching(true);
        Camera camera = new Camera(1_280, 720);

        PreparationCameraPlacement.apply(camera, player, false);

        assertThat(player.position().y() - camera.getLocation().y)
                .isCloseTo(
                        PreparationCameraPlacement.CROUCHING_CAMERA_DROP_METRES, within(0.000001d));
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
}
