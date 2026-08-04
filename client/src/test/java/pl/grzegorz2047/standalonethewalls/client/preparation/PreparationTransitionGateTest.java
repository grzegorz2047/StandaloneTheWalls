package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationTransitionGateTest {
    @Test
    void staysIdleUntilAVerifiedSceneExists() {
        PreparationTransitionGate gate = new PreparationTransitionGate();

        assertThat(gate.poll(Optional.empty())).isEmpty();
        assertThat(gate.currentState()).isEmpty();
    }

    @Test
    void entersExactlyAtTheAuthoritativeSpawn() throws PreparationSceneLoadException {
        PreparationTransitionGate gate = new PreparationTransitionGate();
        VerifiedPreparationScene scene = verifiedScene(LobbyTeam.GREEN, 0, -15.0d, -14.0d, 45.0d);

        PreparationPlayerState entered = gate.poll(Optional.of(scene)).orElseThrow();

        assertThat(entered.scene()).isSameAs(scene);
        assertThat(entered.position()).isEqualTo(new MapVector3(-15.0d, 0.5d, -14.0d));
        assertThat(entered.yawDegrees()).isEqualTo(45.0d);
        assertThat(gate.currentState()).containsSame(entered);
    }

    @Test
    void repeatedPollingCannotReplaceTheFirstVerifiedScene()
            throws PreparationSceneLoadException {
        PreparationTransitionGate gate = new PreparationTransitionGate();
        VerifiedPreparationScene first =
                verifiedScene(LobbyTeam.GREEN, 0, -15.0d, -14.0d, 45.0d);
        VerifiedPreparationScene later =
                verifiedScene(LobbyTeam.BLUE, 10, 3.0d, -14.0d, 135.0d);

        PreparationPlayerState entered = gate.poll(Optional.of(first)).orElseThrow();

        assertThat(gate.poll(Optional.of(later))).isEmpty();
        assertThat(gate.currentState()).containsSame(entered);
        assertThat(gate.currentState().orElseThrow().scene()).isSameAs(first);
    }

    private static VerifiedPreparationScene verifiedScene(
            LobbyTeam team, int spawnIndex, double x, double z, double yawDegrees)
            throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        team,
                        spawnIndex,
                        x,
                        0.5d,
                        z,
                        yawDegrees);
        return PreparationSceneLoader.loadDefault(assignment);
    }
}
