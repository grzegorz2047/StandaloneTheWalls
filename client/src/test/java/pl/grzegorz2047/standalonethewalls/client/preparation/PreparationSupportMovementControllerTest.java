package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jme3.asset.DesktopAssetManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationSupportMovementControllerTest {
    @Test
    void stepsUpAndDownTheVerifiedHalfMetrePlatformWhileGrounded()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = scene();
        PreparationCollisionWorld collisions = collisions(scene);
        PreparationPlayerState ground =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-12.0d, 0.5d, -9.5d, 0.0d, true, 0.0d, 0.0d);

        PreparationPlayerState platform =
                PreparationMovementController.move(
                        ground, collisions, 1.0d, 0.0d, false, false, false, 0.1d);
        PreparationPlayerState descended =
                PreparationMovementController.move(
                        platform, collisions, -1.0d, 0.0d, false, false, false, 0.1d);

        assertThat(platform.position()).isEqualTo(new MapVector3(-11.5d, 1.0d, -9.5d));
        assertThat(platform.grounded()).isTrue();
        assertThat(platform.verticalVelocityMetresPerSecond()).isZero();
        assertThat(descended.position()).isEqualTo(new MapVector3(-12.0d, 0.5d, -9.5d));
        assertThat(descended.grounded()).isTrue();
    }

    @Test
    void jumpsAndLandsOnTheRaisedSupportInsteadOfSpawnHeight()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = scene();
        PreparationCollisionWorld collisions = collisions(scene);
        PreparationPlayerState state =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-9.5d, 1.0d, -9.5d, 0.0d, true, 0.0d, 0.0d);

        state =
                PreparationMovementController.move(
                        state, collisions, 0.0d, 0.0d, false, false, true, 0.05d);
        for (int index = 0; index < 30 && !state.grounded(); index++) {
            state =
                    PreparationMovementController.move(
                            state, collisions, 0.0d, 0.0d, false, false, false, 0.05d);
        }

        assertThat(state.position().y()).isCloseTo(1.0d, within(0.000001d));
        assertThat(state.verticalVelocityMetresPerSecond()).isZero();
        assertThat(state.grounded()).isTrue();
    }

    @Test
    void aDropLargerThanHalfAMetreBecomesAirborne()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene base = scene();
        List<PreparationSupportBox> boxes = new ArrayList<>(base.supportMap().boxes());
        boxes.add(
                new PreparationSupportBox(
                        "TallSupportCollision",
                        new MapVector3(-17.0d, 0.0d, -11.0d),
                        new MapVector3(-16.0d, 1.0d, -7.0d)));
        VerifiedPreparationScene scene = withSupports(base, new PreparationSupportMap(boxes));
        PreparationCollisionWorld collisions = collisions(scene);
        PreparationPlayerState elevated =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-16.0d, 1.5d, -9.0d, 0.0d, true, 0.0d, 0.0d);

        PreparationPlayerState falling =
                PreparationMovementController.move(
                        elevated, collisions, 1.0d, 0.0d, false, false, false, 0.1d);

        assertThat(falling.position().x()).isEqualTo(-15.5d);
        assertThat(falling.position().y()).isBetween(0.5d, 1.5d);
        assertThat(falling.verticalVelocityMetresPerSecond()).isNegative();
        assertThat(falling.grounded()).isFalse();
    }

    private static VerifiedPreparationScene scene() throws PreparationSceneLoadException {
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

    private static VerifiedPreparationScene withSupports(
            VerifiedPreparationScene scene, PreparationSupportMap supports) {
        return new VerifiedPreparationScene(
                scene.mapId(),
                scene.mapSha256(),
                scene.sceneGlb(),
                scene.collisionGlb(),
                scene.sceneDocument(),
                scene.collisionDocument(),
                supports,
                scene.obstacleMap(),
                scene.region(),
                scene.spawn());
    }

    private static PreparationCollisionWorld collisions(VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        return PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);
    }
}
