package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationObstacleSlidingControllerTest {
    @Test
    void slidesAlongTheFreeAxisUsingTheVerifiedCentralWall()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = broadCentralWallScene();
        PreparationPlayerState player = PreparationPlayerState.atAuthoritativeSpawn(scene);
        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);

        PreparationPlayerState sliding =
                PreparationMovementController.move(player, collisions, 1.0d, 1.0d, 0.1d);

        assertThat(sliding.position().x()).isEqualTo(-0.86d);
        assertThat(sliding.position().z()).isCloseTo(-1.6464466d, within(0.000001d));
        assertThat(sliding.position().y()).isEqualTo(0.5d);
        assertThat(sliding.grounded()).isTrue();
    }

    private static VerifiedPreparationScene broadCentralWallScene()
            throws PreparationSceneLoadException {
        VerifiedPreparationScene base = PreparationSceneLoader.loadDefault(assignment());
        PreparationRegion region =
                new PreparationRegion(
                        PreparationTeam.GREEN,
                        new MapVector3(-18.0d, 0.0d, -18.0d),
                        new MapVector3(18.0d, 6.0d, 18.0d));
        PreparationMapSpawn spawn =
                new PreparationMapSpawn(
                        0, PreparationTeam.GREEN, new MapVector3(-0.86d, 0.5d, -2.0d), 0.0d);
        return new VerifiedPreparationScene(
                base.mapId(),
                base.mapSha256(),
                base.sceneGlb(),
                base.collisionGlb(),
                base.sceneDocument(),
                base.collisionDocument(),
                base.supportMap(),
                region,
                spawn);
    }

    private static PreparationSpawnAssignment assignment() {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        return new PreparationSpawnAssignment(
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
    }
}
