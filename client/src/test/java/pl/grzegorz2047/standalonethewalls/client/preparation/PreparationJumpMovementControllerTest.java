package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationJumpMovementControllerTest {
    @Test
    void startsOneDeterministicJumpAndLandsExactlyOnFlatGround()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);

        PreparationPlayerState state =
                PreparationMovementController.move(
                        spawn, collisions, 0.0d, 0.0d, false, false, true, 0.05d);

        assertThat(state.grounded()).isFalse();
        assertThat(state.position().y()).isCloseTo(0.7775d, within(0.0000001d));
        assertThat(state.verticalVelocityMetresPerSecond()).isCloseTo(5.1d, within(0.0000001d));

        for (int index = 0; index < 20 && !state.grounded(); index++) {
            state =
                    PreparationMovementController.move(
                            state, collisions, 0.0d, 0.0d, false, false, false, 0.05d);
        }

        assertThat(state.position().y()).isEqualTo(spawn.position().y());
        assertThat(state.verticalVelocityMetresPerSecond()).isZero();
        assertThat(state.grounded()).isTrue();
    }

    @Test
    void ignoresAirJumpAndAllowsSprintMovementDuringAJump()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPlayerState jumping =
                PreparationMovementController.move(
                        spawn, collisions, 1.0d, 0.0d, true, false, true, 0.05d);
        PreparationPlayerState repeatedJump =
                PreparationMovementController.move(
                        jumping, collisions, 0.0d, 0.0d, false, false, true, 0.05d);
        PreparationPlayerState noJump =
                PreparationMovementController.move(
                        jumping, collisions, 0.0d, 0.0d, false, false, false, 0.05d);

        assertThat(horizontalDistance(spawn, jumping)).isCloseTo(0.4d, within(0.000001d));
        assertThat(repeatedJump.position()).isEqualTo(noJump.position());
        assertThat(repeatedJump.verticalVelocityMetresPerSecond())
                .isEqualTo(noJump.verticalVelocityMetresPerSecond());
    }

    @Test
    void rejectsCrouchAndJumpInTheSamePredictedStep()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);

        assertThatThrownBy(
                        () ->
                                PreparationMovementController.move(
                                        spawn, collisions, 0.0d, 0.0d, false, true, true, 0.05d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PreparationPlayerState player() throws PreparationSceneLoadException {
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
        return PreparationPlayerState.atAuthoritativeSpawn(
                PreparationSceneLoader.loadDefault(assignment));
    }

    private static PreparationCollisionWorld collisions(PreparationPlayerState player)
            throws PreparationSceneGraphException {
        return PreparationCollisionWorld.load(new DesktopAssetManager(true), player.scene());
    }

    private static double horizontalDistance(
            PreparationPlayerState first, PreparationPlayerState second) {
        return Math.hypot(
                second.position().x() - first.position().x(),
                second.position().z() - first.position().z());
    }
}
