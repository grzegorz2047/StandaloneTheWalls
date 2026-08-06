package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationSupportPredictionHistoryTest {
    @Test
    void replaysAnUnacknowledgedStepOntoTheAuthoritativeSupportHeight()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = scene();
        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);
        PreparationPlayerState ground =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-12.0d, 0.5d, -9.5d, 0.0d, true, 0.0d, 0.0d);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        PreparationPlayerState predicted =
                history.predict(
                        ground, collisions, 1L, 1.0d, 0.0d, false, false, false, 0.1d);
        history.markSubmitted(1L);
        PreparationPlayerState reconciled = history.reconcile(ground, collisions, 0L);

        assertThat(predicted.position())
                .isEqualTo(new MapVector3(-11.5d, 1.0d, -9.5d));
        assertThat(reconciled.position()).isEqualTo(predicted.position());
        assertThat(reconciled.grounded()).isTrue();
        assertThat(reconciled.groundHeightMetres()).isEqualTo(1.0d);
        assertThat(history.pendingStepCount()).isOne();
    }

    @Test
    void authoritativePlatformAcknowledgementDoesNotReplaySpawnHeight()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = scene();
        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);
        PreparationPlayerState ground =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-12.0d, 0.5d, -9.5d, 0.0d, true, 0.0d, 0.0d);
        PreparationPredictionHistory history = new PreparationPredictionHistory();
        PreparationPlayerState platform =
                history.predict(
                        ground, collisions, 1L, 1.0d, 0.0d, false, false, false, 0.1d);
        history.markSubmitted(1L);
        history.predict(
                platform, collisions, 2L, 1.0d, 0.0d, false, false, false, 0.1d);
        PreparationPlayerState authoritative =
                ground.withAuthoritativeState(-11.5d, 1.0d, -9.5d, 0.0d, true, 0.0d, 0.0d);

        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);

        assertThat(reconciled.position())
                .isEqualTo(new MapVector3(-11.0d, 1.0d, -9.5d));
        assertThat(reconciled.grounded()).isTrue();
        assertThat(reconciled.groundHeightMetres()).isEqualTo(1.0d);
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
}
