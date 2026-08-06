package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportBox;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

class PreparationObstacleMovementSimulationTest {
    private static final PlayerId ALPHA = new PlayerId("sf1_" + "a".repeat(52));
    private static final byte[] MAP_DIGEST = new byte[32];
    private static final PreparationSupportMap GROUND =
            new PreparationSupportMap(
                    List.of(
                            new PreparationSupportBox(
                                    "GroundCollision",
                                    new MapVector3(-10.0d, -1.0d, -10.0d),
                                    new MapVector3(10.0d, 0.0d, 10.0d))));

    @Test
    void blocksSprintBeforeThePlayerBodyCrossesAThinWall() {
        PreparationMovementSimulation simulation =
                simulation(
                        -0.8d,
                        0.5d,
                        0.0d,
                        obstacle("ThinWallCollision", -0.4d, 0.0d, -2.0d, -0.39d, 5.0d, 2.0d));

        PreparationPlayerSnapshot blocked =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, true, false, false, 0, 0))));

        assertThat(blocked.xMillimetres()).isEqualTo(-800);
        assertThat(blocked.zMillimetres()).isZero();
        assertThat(blocked.grounded()).isTrue();
    }

    @Test
    void slidesAlongTheFreeAxisWhenDiagonalMovementMeetsAWall() {
        PreparationMovementSimulation simulation =
                simulation(
                        -0.46d,
                        0.5d,
                        0.0d,
                        obstacle("CentralWallZCollision", -0.1d, 0.0d, -2.0d, 0.1d, 5.0d, 2.0d));

        PreparationPlayerSnapshot sliding =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 127, false, false, false, 0, 0))));

        assertThat(sliding.xMillimetres()).isEqualTo(-460);
        assertThat(sliding.zMillimetres()).isPositive();
        assertThat(sliding.grounded()).isTrue();
    }

    @Test
    void ignoresAnObstacleWhoseVerticalRangeDoesNotMeetThePlayerBody() {
        PreparationMovementSimulation simulation =
                simulation(
                        -0.5d,
                        0.5d,
                        0.0d,
                        obstacle("OverheadWallCollision", -0.1d, 2.0d, -2.0d, 0.1d, 3.0d, 2.0d));

        PreparationPlayerSnapshot moved =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, true, false, false, 0, 0))));

        assertThat(moved.xMillimetres()).isEqualTo(-100);
        assertThat(moved.grounded()).isTrue();
    }

    @Test
    void keepsTheAcceptedCrouchUntilStandingClearanceBecomesAvailable() {
        PreparationMovementSimulation simulation =
                simulation(
                        -0.8d,
                        0.5d,
                        0.0d,
                        obstacle("LowWallCollision", -0.4d, 1.15d, -2.0d, 2.0d, 1.35d, 2.0d));

        PreparationPlayerSnapshot entered =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, false, true, false, 0, 0))));
        PreparationPlayerSnapshot blockedStanding =
                player(
                        simulation.advanceTick(
                                12L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 2L, 0, 0, false, false, false, 0, 0))));
        PreparationPlayerSnapshot exited =
                player(
                        simulation.advanceTick(
                                13L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 3L, -127, 0, false, false, false, 0, 0))));
        PreparationPlayerSnapshot standing =
                player(
                        simulation.advanceTick(
                                14L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 4L, 0, 0, false, false, false, 0, 0))));

        assertThat(entered.xMillimetres()).isEqualTo(-650);
        assertThat(entered.crouching()).isTrue();
        assertThat(blockedStanding.crouching()).isTrue();
        assertThat(exited.xMillimetres()).isEqualTo(-800);
        assertThat(exited.crouching()).isTrue();
        assertThat(standing.crouching()).isFalse();
    }

    @Test
    void stopsAnAuthoritativeJumpAtTheCeilingAndThenFalls() {
        PreparationMovementSimulation simulation =
                simulation(
                        0.0d,
                        0.5d,
                        0.0d,
                        obstacle("CeilingWallCollision", -2.0d, 2.0d, -2.0d, 2.0d, 2.2d, 2.0d));

        PreparationPlayerSnapshot hit =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 0, 0, false, false, true, 0, 0))));
        PreparationPlayerSnapshot falling =
                player(
                        simulation.advanceTick(
                                12L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 2L, 0, 0, false, false, false, 0, 0))));

        assertThat(hit.yMillimetres()).isEqualTo(700);
        assertThat(hit.verticalVelocityMillimetresPerSecond()).isZero();
        assertThat(hit.grounded()).isFalse();
        assertThat(falling.yMillimetres()).isLessThan(hit.yMillimetres());
        assertThat(falling.verticalVelocityMillimetresPerSecond()).isNegative();
    }

    @Test
    void rejectsASpawnWithoutAuthoritativeStandingClearance() {
        PreparationObstacleMap obstacles =
                new PreparationObstacleMap(
                        List.of(
                                obstacle(
                                        "SpawnWallCollision",
                                        -0.25d,
                                        0.0d,
                                        -0.25d,
                                        0.25d,
                                        5.0d,
                                        0.25d)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> map(0.0d, 0.5d, 0.0d, obstacles))
                .withMessageContaining("obstacle");
    }

    private static PreparationMovementSimulation simulation(
            double spawnX, double spawnY, double spawnZ, PreparationObstacleBox obstacle) {
        PreparationObstacleMap obstacles = new PreparationObstacleMap(List.of(obstacle));
        PreparationMapDefinition map = map(spawnX, spawnY, spawnZ, obstacles);
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
                        spawnZ,
                        0.0d);
        return PreparationMovementSimulation.start(2L, 10L, map, Map.of(ALPHA, assignment));
    }

    private static PreparationMapDefinition map(
            double spawnX, double spawnY, double spawnZ, PreparationObstacleMap obstacles) {
        return new PreparationMapDefinition(
                "minimal_preparation",
                MAP_DIGEST,
                List.of(new PreparationSpawnPoint(0, TeamId.RED, spawnX, spawnY, spawnZ, 0.0d)),
                Map.of(
                        TeamId.RED,
                        new PreparationRegionBounds(
                                TeamId.RED, -10_000, -1_000, -10_000, 10_000, 6_000, 10_000)),
                GROUND,
                obstacles);
    }

    private static PreparationObstacleBox obstacle(
            String name,
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {
        return new PreparationObstacleBox(
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
