package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationCameraPlacementTest {
    @Test
    void appliesTheExactAuthoritativeSpawnAndProtocolYaw() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedScene());
        Camera camera = new Camera(1280, 720);

        PreparationCameraPlacement.apply(camera, player);

        assertThat(camera.getLocation()).isEqualTo(new Vector3f(-15.0f, 0.5f, -14.0f));
        float expected = (float) (Math.sqrt(2.0d) / 2.0d);
        assertThat(camera.getDirection().x).isCloseTo(expected, within(0.00001f));
        assertThat(camera.getDirection().y).isCloseTo(0.0f, within(0.00001f));
        assertThat(camera.getDirection().z).isCloseTo(expected, within(0.00001f));
    }

    @Test
    void appliesBoundedVerticalPitchWithoutChangingYaw() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedScene())
                        .rotateView(0.0d, 30.0d);
        Camera camera = new Camera(1280, 720);

        PreparationCameraPlacement.apply(camera, player);

        float horizontal = (float) (Math.cos(Math.toRadians(30.0d)) * Math.sqrt(2.0d) / 2.0d);
        assertThat(camera.getDirection().x).isCloseTo(horizontal, within(0.00001f));
        assertThat(camera.getDirection().y).isCloseTo(0.5f, within(0.00001f));
        assertThat(camera.getDirection().z).isCloseTo(horizontal, within(0.00001f));
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
