package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationCollisionWorldTest {
    @Test
    void loadsTheVerifiedInvisibleCollisionGraph()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);

        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(assetManager, verifiedGreenScene());

        assertThat(collisions.hasGroundSupport(new MapVector3(-15.0d, 0.5d, -14.0d))).isTrue();
    }

    @Test
    void requiresVerifiedGroundBelowTheTarget()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationCollisionWorld collisions = collisionWorld();

        assertThat(collisions.hasGroundSupport(new MapVector3(10.0d, 0.5d, 10.0d))).isTrue();
        assertThat(collisions.hasGroundSupport(new MapVector3(25.0d, 0.5d, 25.0d))).isFalse();
        assertThat(
                        collisions.permitsHorizontal(
                                new MapVector3(10.0d, 0.5d, 10.0d),
                                new MapVector3(25.0d, 0.5d, 25.0d)))
                .isFalse();
    }

    @Test
    void blocksCrossingTheCentralObstacle()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationCollisionWorld collisions = collisionWorld();

        assertThat(
                        collisions.permitsHorizontal(
                                new MapVector3(-2.0d, 0.5d, -10.0d),
                                new MapVector3(2.0d, 0.5d, -10.0d)))
                .isFalse();
        assertThat(
                        collisions.permitsHorizontal(
                                new MapVector3(-10.0d, 0.5d, -2.0d),
                                new MapVector3(-10.0d, 0.5d, 2.0d)))
                .isFalse();
    }

    @Test
    void permitsSupportedMovementThatDoesNotMeetAnObstacle()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationCollisionWorld collisions = collisionWorld();

        assertThat(
                        collisions.permitsHorizontal(
                                new MapVector3(-15.0d, 0.5d, -14.0d),
                                new MapVector3(-12.0d, 0.5d, -10.0d)))
                .isTrue();
        assertThat(
                        collisions.permitsHorizontal(
                                new MapVector3(-12.0d, 0.5d, -10.0d),
                                new MapVector3(-12.0d, 0.5d, -10.0d)))
                .isTrue();
    }

    @Test
    void rejectsVerticalMovementQueries()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationCollisionWorld collisions = collisionWorld();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertThat(
                                        collisions.permitsHorizontal(
                                                new MapVector3(-15.0d, 0.5d, -14.0d),
                                                new MapVector3(-15.0d, 1.0d, -14.0d)))
                                .isFalse());
    }

    private static PreparationCollisionWorld collisionWorld()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        return PreparationCollisionWorld.load(new DesktopAssetManager(true), verifiedGreenScene());
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
