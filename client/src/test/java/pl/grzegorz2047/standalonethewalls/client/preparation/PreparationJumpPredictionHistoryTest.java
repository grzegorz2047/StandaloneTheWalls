package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationJumpPredictionHistoryTest {
    @Test
    void replaysAnUnacknowledgedJumpEdgeFromTheCorrectedGroundedState()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState walking =
                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, false, false, false, 0.05d);
        history.markSubmitted(1L);
        history.predict(walking, collisions, 2L, 0.0d, 0.0d, false, false, true, 0.05d);
        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-14.8d, 0.5d, -13.8d, 0.0d, true, 45.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);
        PreparationPlayerState expected =
                PreparationMovementController.move(
                        authoritative, collisions, 0.0d, 0.0d, false, false, true, 0.05d);

        assertThat(reconciled.position()).isEqualTo(expected.position());
        assertThat(reconciled.verticalVelocityMetresPerSecond())
                .isEqualTo(expected.verticalVelocityMetresPerSecond());
        assertThat(reconciled.grounded()).isFalse();
        assertThat(history.pendingStepCount()).isOne();
    }

    @Test
    void preservesAuthoritativeAirborneStateBeforeReplayingTheTail()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState spawn = player();
        PreparationCollisionWorld collisions = collisions(spawn);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState jumping =
                history.predict(spawn, collisions, 1L, 0.0d, 0.0d, false, false, true, 0.05d);
        history.markSubmitted(1L);
        history.predict(jumping, collisions, 2L, 1.0d, 0.0d, true, false, false, 0.05d);
        PreparationPlayerState authoritative =
                spawn.withAuthoritativeState(-15.0d, 0.8d, -14.0d, 4.0d, false, 45.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);
        PreparationPlayerState expected =
                PreparationMovementController.move(
                        authoritative, collisions, 1.0d, 0.0d, true, false, false, 0.05d);

        assertThat(reconciled.position()).isEqualTo(expected.position());
        assertThat(reconciled.verticalVelocityMetresPerSecond())
                .isEqualTo(expected.verticalVelocityMetresPerSecond());
        assertThat(reconciled.grounded()).isEqualTo(expected.grounded());
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
}
