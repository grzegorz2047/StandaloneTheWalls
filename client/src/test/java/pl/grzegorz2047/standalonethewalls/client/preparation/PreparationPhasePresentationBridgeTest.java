package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationPhasePresentationBridgeTest {
    @AfterEach
    void clearActiveScene() {
        PreparationPhasePresentationBridge.clear();
    }

    @Test
    void opensTheBoundSceneOnlyForOpenCombatAndNeverClosesItAgain()
            throws PreparationSceneLoadException {
        VerifiedPreparationScene scene = PreparationSceneLoader.loadDefault(assignment());
        PreparationPhasePresentationBridge.bind(scene);

        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.WALLS_OPENING);
        assertThat(scene.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.CLOSED);

        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.OPEN_COMBAT);
        assertThat(scene.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);

        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.PREPARATION);
        assertThat(scene.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
    }

    @Test
    void appliesLatchedOpenCombatWhenTheSceneBindsAfterThePhase()
            throws PreparationSceneLoadException {
        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.OPEN_COMBAT);
        VerifiedPreparationScene scene = PreparationSceneLoader.loadDefault(assignment());

        PreparationPhasePresentationBridge.bind(scene);

        assertThat(scene.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
    }

    @Test
    void preparationResetsTheLatchBeforeABrandNewSceneBinds()
            throws PreparationSceneLoadException {
        VerifiedPreparationScene firstRound = PreparationSceneLoader.loadDefault(assignment());
        PreparationPhasePresentationBridge.bind(firstRound);
        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.OPEN_COMBAT);
        assertThat(firstRound.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);

        PreparationPhasePresentationBridge.apply(LobbyMatchPhase.PREPARATION);
        VerifiedPreparationScene nextRound = PreparationSceneLoader.loadDefault(assignment());
        PreparationPhasePresentationBridge.bind(nextRound);

        assertThat(firstRound.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
        assertThat(nextRound.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.CLOSED);
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
