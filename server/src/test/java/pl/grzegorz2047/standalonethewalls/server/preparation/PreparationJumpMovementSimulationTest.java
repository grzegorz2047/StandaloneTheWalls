package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

class PreparationJumpMovementSimulationTest {
    private static final PlayerId ALPHA = new PlayerId("sf1_" + "a".repeat(52));
    private static final byte[] MAP_DIGEST = new byte[32];

    @Test
    void startsOneAuthoritativeJumpAndLandsExactlyOnSpawnGround() {
        PreparationMovementSimulation simulation = simulation();
        PreparationWorldSnapshot first =
                simulation.advanceTick(
                        11L,
                        Map.of(
                                ALPHA,
                                new PreparationInput(
                                        2L, 1L, 0, 0, false, false, true, 0, 0)));

        PreparationPlayerSnapshot state = player(first);
        assertThat(state.yMillimetres()).isEqualTo(278);
        assertThat(state.verticalVelocityMillimetresPerSecond()).isEqualTo(5_100);
        assertThat(state.grounded()).isFalse();

        long tick = 11L;
        while (!state.grounded() && tick < 40L) {
            state = player(simulation.advanceTick(++tick, Map.of()));
        }

        assertThat(state.yMillimetres()).isZero();
        assertThat(state.verticalVelocityMillimetresPerSecond()).isZero();
        assertThat(state.grounded()).isTrue();

        PreparationPlayerSnapshot afterLanding =
                player(simulation.advanceTick(++tick, Map.of()));
        assertThat(afterLanding.yMillimetres()).isZero();
        assertThat(afterLanding.grounded()).isTrue();
    }

    @Test
    void consumesAnAirJumpWithoutBufferingItForLanding() {
        PreparationMovementSimulation simulation = simulation();
        simulation.advanceTick(
                11L,
                Map.of(
                        ALPHA,
                        new PreparationInput(2L, 1L, 0, 0, false, false, true, 0, 0)));
        PreparationPlayerSnapshot airborne =
                player(
                        simulation.advanceTick(
                                12L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 2L, 0, 0, false, false, true, 0, 0))));
        assertThat(airborne.grounded()).isFalse();

        long tick = 12L;
        PreparationPlayerSnapshot state = airborne;
        while (!state.grounded() && tick < 40L) {
            state = player(simulation.advanceTick(++tick, Map.of()));
        }
        PreparationPlayerSnapshot afterLanding =
                player(simulation.advanceTick(++tick, Map.of()));

        assertThat(state.grounded()).isTrue();
        assertThat(afterLanding.grounded()).isTrue();
        assertThat(afterLanding.yMillimetres()).isZero();
        assertThat(afterLanding.verticalVelocityMillimetresPerSecond()).isZero();
        assertThat(afterLanding.lastProcessedInputSequence()).isEqualTo(2L);
    }

    @Test
    void allowsAuthoritativeSprintMovementDuringTheJumpTick() {
        PreparationMovementSimulation simulation = simulation();

        PreparationPlayerSnapshot state =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, true, false, true, 0, 0))));

        assertThat(state.xMillimetres()).isEqualTo(400);
        assertThat(state.yMillimetres()).isEqualTo(278);
        assertThat(state.verticalVelocityMillimetresPerSecond()).isEqualTo(5_100);
        assertThat(state.grounded()).isFalse();
    }

    private static PreparationMovementSimulation simulation() {
        PreparationMapDefinition map =
                new PreparationMapDefinition(
                        "minimal_preparation",
                        MAP_DIGEST,
                        List.of(new PreparationSpawnPoint(0, TeamId.RED, 0.0d, 0.0d, 0.0d, 0.0d)),
                        Map.of(
                                TeamId.RED,
                                new PreparationRegionBounds(
                                        TeamId.RED,
                                        -1_000,
                                        -1_000,
                                        -1_000,
                                        1_000,
                                        1_000,
                                        1_000)));
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        4L,
                        2L,
                        "minimal_preparation",
                        MAP_DIGEST,
                        LobbyTeam.RED,
                        0,
                        0.0d,
                        0.0d,
                        0.0d,
                        0.0d);
        return PreparationMovementSimulation.start(2L, 10L, map, Map.of(ALPHA, assignment));
    }

    private static PreparationPlayerSnapshot player(PreparationWorldSnapshot snapshot) {
        return snapshot.players().stream()
                .filter(player -> player.playerId().equals(ALPHA))
                .findFirst()
                .orElseThrow();
    }
}
