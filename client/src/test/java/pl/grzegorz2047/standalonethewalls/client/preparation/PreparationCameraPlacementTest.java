package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationCameraPlacementTest {
    @Test
    void appliesTheExactAuthoritativeSpawnAndYaw() throws PreparationSceneLoadException {
        PreparationPlayerState player =
                PreparationPlayerState.atAuthoritativeSpawn(verifiedGreenScene());
        Camera camera = new Camera(1280, 720);

        PreparationCameraPlacement.apply(camera, player);

        assertThat(camera.getLocation()).isEqualTo(new Vector3f(-15.0f, 0.5f, -14.0f));
        Quaternion expected =
                new Quaternion().fromAngleAxis(45.0f * FastMath.DEG_TO_RAD, Vector3f.UNIT_Y);
        assertThat(camera.getRotation()).isEqualTo(expected);
    }

    private static VerifiedPreparationScene verifiedGreenScene()
            throws PreparationSceneLoadException {
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
