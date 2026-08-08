package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import com.jme3.asset.DesktopAssetManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationBarrierPredictionBoundaryTest {
    @Test
    void openingDropsTheClosedPredictionTailAndUsesVerifiedWorldBounds()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = PreparationSceneLoader.loadDefault(assignment());
        PreparationPlayerState closed =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withAuthoritativeState(-1.4d, 0.5d, -14.0d, 0.0d, true, 0.0d, 0.0d);
        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        PreparationPlayerState clamped =
                history.predict(closed, collisions, 1L, 1.0d, 0.0d, true, 0.1d);
        history.markSubmitted(1L);
        assertThat(clamped.position().x()).isEqualTo(-1.0d);
        assertThat(history.pendingStepCount()).isOne();

        assertThat(scene.openCentralBarriers()).isTrue();
        PreparationPlayerState authoritativeOpen =
                closed.withAuthoritativeState(-1.4d, 0.5d, -14.0d, 0.0d, true, 0.0d, 0.0d);
        PreparationPlayerState reset = history.reconcile(authoritativeOpen, collisions, 0L);

        assertThat(reset.position().x()).isEqualTo(-1.4d);
        assertThat(reset.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
        assertThat(history.pendingStepCount()).isZero();

        PreparationPlayerState outsideTeamRegion =
                history.predict(reset, collisions, 2L, 1.0d, 0.0d, true, 0.1d);

        assertThat(outsideTeamRegion.position().x()).isCloseTo(-0.6d, within(1.0e-12d));
        assertThat(outsideTeamRegion.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
        assertThat(history.pendingStepCount()).isOne();
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> outsideTeamRegion.withBarrierPolicy(PreparationBarrierPolicy.CLOSED))
                .withMessageContaining("cannot close");
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
