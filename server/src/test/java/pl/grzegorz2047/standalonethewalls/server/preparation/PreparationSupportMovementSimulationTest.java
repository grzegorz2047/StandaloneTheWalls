package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

class PreparationSupportMovementSimulationTest {
    private static final PlayerId ALPHA = new PlayerId("sf1_" + "a".repeat(52));
    private static final byte[] MAP_DIGEST = new byte[32];

    @Test
    void stepsUpAndDownAHalfMetreSupportWhileGrounded() {
        PreparationSupportMap supports = halfMetreSupports();
        PreparationMovementSimulation simulation = simulation(-0.25d, 0.5d, supports);

        PreparationPlayerSnapshot platform =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, false, false, false, 0, 0))));
        PreparationPlayerSnapshot ground =
                player(
                        simulation.advanceTick(
                                12L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 2L, -127, 0, false, false, false, 0, 0))));

        assertThat(platform.xMillimetres()).isZero();
        assertThat(platform.yMillimetres()).isEqualTo(1_000);
        assertThat(platform.grounded()).isTrue();
        assertThat(ground.xMillimetres()).isEqualTo(-250);
        assertThat(ground.yMillimetres()).isEqualTo(500);
        assertThat(ground.grounded()).isTrue();
    }

    @Test
    void jumpLandsOnTheRaisedSupportHeight() {
        PreparationMovementSimulation simulation = simulation(0.25d, 1.0d, halfMetreSupports());
        PreparationPlayerSnapshot state =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 0, 0, false, false, true, 0, 0))));

        long tick = 11L;
        while (!state.grounded() && tick < 40L) {
            state = player(simulation.advanceTick(++tick, Map.of()));
        }

        assertThat(state.yMillimetres()).isEqualTo(1_000);
        assertThat(state.verticalVelocityMillimetresPerSecond()).isZero();
        assertThat(state.grounded()).isTrue();
    }

    @Test
    void aDropLargerThanHalfAMetreStartsAuthoritativeFalling() {
        PreparationSupportMap supports =
                new PreparationSupportMap(
                        List.of(
                                box("GroundCollision", -2.0d, -1.0d, -2.0d, 2.0d, 0.0d, 2.0d),
                                box("TallSupportCollision", -0.5d, 0.0d, -1.0d, 0.0d, 1.0d, 1.0d)));
        PreparationMovementSimulation simulation = simulation(0.0d, 1.5d, supports);

        PreparationPlayerSnapshot falling =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, false, false, false, 0, 0))));

        assertThat(falling.xMillimetres()).isEqualTo(250);
        assertThat(falling.yMillimetres()).isBetween(500, 1_500);
        assertThat(falling.verticalVelocityMillimetresPerSecond()).isNegative();
        assertThat(falling.grounded()).isFalse();
    }

    private static PreparationMovementSimulation simulation(
            double spawnX, double spawnY, PreparationSupportMap supports) {
        PreparationMapDefinition map =
                new PreparationMapDefinition(
                        "minimal_preparation",
                        MAP_DIGEST,
                        List.of(
                                new PreparationSpawnPoint(
                                        0, TeamId.RED, spawnX, spawnY, 0.0d, 0.0d)),
                        Map.of(
                                TeamId.RED,
                                new PreparationRegionBounds(
                                        TeamId.RED, -2_000, -1_000, -2_000, 2_000, 4_000, 2_000)),
                        supports);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        4L,
                        2L,
                        "minimal_preparation",
                        MAP_DIGEST,
                        LobbyTeam.RED,
                        0,
                        spawnX,
                        spawnY,
                        0.0d,
                        0.0d);
        return PreparationMovementSimulation.start(2L, 10L, map, Map.of(ALPHA, assignment));
    }

    private static PreparationSupportMap halfMetreSupports() {
        return new PreparationSupportMap(
                List.of(
                        box("GroundCollision", -2.0d, -1.0d, -2.0d, 2.0d, 0.0d, 2.0d),
                        box("StepSupportCollision", 0.0d, 0.0d, -1.0d, 1.0d, 0.5d, 1.0d)));
    }

    private static PreparationSupportBox box(
            String name,
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {
        return new PreparationSupportBox(
                name,
                new MapVector3(minimumX, minimumY, minimumZ),
                new MapVector3(maximumX, maximumY, maximumZ));
    }

    private static PreparationPlayerSnapshot player(PreparationWorldSnapshot snapshot) {
        return snapshot.players().stream()
                .filter(player -> player.playerId().equals(ALPHA))
                .findFirst()
                .orElseThrow();
    }
}
