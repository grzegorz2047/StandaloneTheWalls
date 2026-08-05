package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class PreparationMovementSimulationTest {
    private static final PlayerId ALPHA = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId BRAVO = new PlayerId("sf1_" + "b".repeat(52));
    private static final byte[] MAP_DIGEST = new byte[32];

    @Test
    void advancesLatestIntentAtFixedSpeedAndAcknowledgesItsSequence() {
        PreparationMovementSimulation simulation = simulation();

        PreparationWorldSnapshot snapshot =
                simulation.advanceTick(
                        11L, Map.of(ALPHA, new PreparationInput(2L, 7L, 127, 0, false, 0, -250)));

        PreparationPlayerSnapshot alpha = player(snapshot, ALPHA);
        assertThat(alpha.lastProcessedInputSequence()).isEqualTo(7L);
        assertThat(alpha.xMillimetres()).isEqualTo(250);
        assertThat(alpha.yMillimetres()).isZero();
        assertThat(alpha.zMillimetres()).isZero();
        assertThat(alpha.pitchCentidegrees()).isEqualTo(-250);
        assertThat(snapshot.players()).extracting(player -> player.playerId().value()).isSorted();
    }

    @Test
    void normalizesDiagonalMovementAndRetainsIntentUntilAZeroInputArrives() {
        PreparationMovementSimulation simulation = simulation();
        PreparationWorldSnapshot first =
                simulation.advanceTick(
                        11L, Map.of(ALPHA, new PreparationInput(2L, 1L, 127, 127, false, 0, 0)));
        PreparationWorldSnapshot second = simulation.advanceTick(12L, Map.of());
        PreparationWorldSnapshot stopped =
                simulation.advanceTick(
                        13L, Map.of(ALPHA, new PreparationInput(2L, 2L, 0, 0, false, 0, 0)));
        PreparationWorldSnapshot afterStop = simulation.advanceTick(14L, Map.of());

        assertThat(player(first, ALPHA).xMillimetres()).isEqualTo(177);
        assertThat(player(first, ALPHA).zMillimetres()).isEqualTo(177);
        assertThat(player(second, ALPHA).xMillimetres()).isEqualTo(354);
        assertThat(player(second, ALPHA).zMillimetres()).isEqualTo(354);
        assertThat(player(stopped, ALPHA).lastProcessedInputSequence()).isEqualTo(2L);
        assertThat(player(afterStop, ALPHA).xMillimetres())
                .isEqualTo(player(stopped, ALPHA).xMillimetres());
        assertThat(player(afterStop, ALPHA).zMillimetres())
                .isEqualTo(player(stopped, ALPHA).zMillimetres());
    }

    @Test
    void appliesAuthoritativeSprintSpeedAndNormalizesItsDiagonal() {
        PreparationMovementSimulation walkingSimulation = simulation();
        PreparationMovementSimulation sprintingSimulation = simulation();
        PreparationMovementSimulation diagonalSimulation = simulation();

        PreparationWorldSnapshot walking =
                walkingSimulation.advanceTick(
                        11L, Map.of(ALPHA, new PreparationInput(2L, 1L, 127, 0, false, 0, 0)));
        PreparationWorldSnapshot sprinting =
                sprintingSimulation.advanceTick(
                        11L, Map.of(ALPHA, new PreparationInput(2L, 1L, 127, 0, true, 0, 0)));
        PreparationWorldSnapshot diagonal =
                diagonalSimulation.advanceTick(
                        11L, Map.of(ALPHA, new PreparationInput(2L, 1L, 127, 127, true, 0, 0)));

        assertThat(player(walking, ALPHA).xMillimetres()).isEqualTo(250);
        assertThat(player(sprinting, ALPHA).xMillimetres()).isEqualTo(400);
        assertThat(player(diagonal, ALPHA).xMillimetres()).isEqualTo(283);
        assertThat(player(diagonal, ALPHA).zMillimetres()).isEqualTo(283);
    }

    @Test
    void clampsMovementToTheVerifiedTeamRegionAndRemovesDisconnectedPlayers() {
        PreparationMovementSimulation simulation = simulation();
        simulation.advanceTick(
                11L, Map.of(ALPHA, new PreparationInput(2L, 1L, 127, 0, false, 0, 0)));
        for (long tick = 12L; tick <= 20L; tick++) {
            simulation.advanceTick(tick, Map.of());
        }

        assertThat(player(simulation.currentSnapshot().orElseThrow(), ALPHA).xMillimetres())
                .isEqualTo(1_000);
        assertThat(simulation.remove(BRAVO)).isTrue();
        assertThat(simulation.currentSnapshot().orElseThrow().players())
                .extracting(PreparationPlayerSnapshot::playerId)
                .containsExactly(ALPHA);
        assertThat(simulation.remove(BRAVO)).isFalse();
        assertThat(simulation.remove(ALPHA)).isTrue();
        assertThat(simulation.playerCount()).isZero();
        assertThat(simulation.currentSnapshot()).isEmpty();
    }

    @Test
    void rejectsWrongRoundUnknownPlayersAndNonMonotonicTicks() {
        PreparationMovementSimulation simulation = simulation();

        assertThatThrownBy(
                        () ->
                                simulation.advanceTick(
                                        11L,
                                        Map.of(
                                                ALPHA,
                                                new PreparationInput(3L, 1L, 0, 0, false, 0, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        PlayerId unknown = new PlayerId("sf1_" + "c".repeat(52));
        assertThatThrownBy(
                        () ->
                                simulation.advanceTick(
                                        11L,
                                        Map.of(
                                                unknown,
                                                new PreparationInput(2L, 1L, 0, 0, false, 0, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        simulation.advanceTick(11L, Map.of());
        assertThatThrownBy(() -> simulation.advanceTick(11L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PreparationMovementSimulation simulation() {
        PreparationMapDefinition map =
                new PreparationMapDefinition(
                        "minimal_preparation",
                        MAP_DIGEST,
                        List.of(
                                new PreparationSpawnPoint(0, TeamId.RED, 0.0d, 0.0d, 0.0d, 0.0d),
                                new PreparationSpawnPoint(
                                        1, TeamId.BLUE, 10.0d, 0.0d, 10.0d, 90.0d)),
                        Map.of(
                                TeamId.RED,
                                new PreparationRegionBounds(
                                        TeamId.RED, -1_000, -1_000, -1_000, 1_000, 1_000, 1_000),
                                TeamId.BLUE,
                                new PreparationRegionBounds(
                                        TeamId.BLUE, 9_000, -1_000, 9_000, 11_000, 1_000, 11_000)));
        return PreparationMovementSimulation.start(
                2L,
                10L,
                map,
                Map.of(
                        ALPHA, assignment(LobbyTeam.RED, 0, 0.0d, 0.0d, 0.0d, 0.0d),
                        BRAVO, assignment(LobbyTeam.BLUE, 1, 10.0d, 0.0d, 10.0d, 90.0d)));
    }

    private static PreparationSpawnAssignment assignment(
            LobbyTeam team, int index, double x, double y, double z, double yaw) {
        return new PreparationSpawnAssignment(
                4L, 2L, "minimal_preparation", MAP_DIGEST, team, index, x, y, z, yaw);
    }

    private static PreparationPlayerSnapshot player(
            PreparationWorldSnapshot snapshot, PlayerId playerId) {
        return snapshot.players().stream()
                .filter(player -> player.playerId().equals(playerId))
                .findFirst()
                .orElseThrow();
    }
}
