package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchSnapshot;

class PreparationTransitionPlannerTest {
    @Test
    void plansTheCompleteDeterministicParticipantTargetedDeliveryBeforeSending() {
        LobbyRosterState roster =
                roster(
                        participant("alpha", TeamId.GREEN),
                        participant("bravo", TeamId.BLUE),
                        participant("charlie", TeamId.GREEN));
        PreparationMapDefinition map =
                map(
                        new PreparationSpawnPoint(8, TeamId.BLUE, 80.0d, 2.0d, 0.0d, 90.0d),
                        new PreparationSpawnPoint(5, TeamId.GREEN, 50.0d, 2.0d, 0.0d, 45.0d),
                        new PreparationSpawnPoint(2, TeamId.GREEN, 20.0d, 2.0d, 0.0d, 0.0d));

        List<PreparationClientSpawn> planned =
                PreparationTransitionPlanner.plan(map, roster, preparation(roster, 4L));

        assertThat(planned)
                .extracting(delivery -> delivery.participantId().value())
                .containsExactly("alpha", "bravo", "charlie");
        assertThat(planned)
                .extracting(delivery -> delivery.assignment().spawnIndex())
                .containsExactly(2, 8, 5)
                .doesNotHaveDuplicates();
        assertThat(planned)
                .extracting(delivery -> delivery.assignment().team())
                .containsExactly(LobbyTeam.GREEN, LobbyTeam.BLUE, LobbyTeam.GREEN);
        assertThat(planned)
                .allSatisfy(
                        delivery -> {
                            assertThat(delivery.assignment().rosterRevision())
                                    .isEqualTo(roster.revision());
                            assertThat(delivery.assignment().roundNumber()).isEqualTo(4L);
                            assertThat(delivery.assignment().mapId()).isEqualTo("arena-one");
                            assertThat(delivery.assignment().mapSha256()).containsExactly(digest());
                        });
        assertThatThrownBy(planned::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsWrongPhaseRosterRevisionAndPlayerCountBeforeAllocation() {
        LobbyRosterState roster =
                roster(participant("alpha", TeamId.GREEN), participant("bravo", TeamId.BLUE));
        PreparationMapDefinition map = completeMap();

        assertCode(
                map,
                roster,
                snapshot(MatchPhase.WAITING_FOR_PLAYERS, roster.revision(), 2, 0L),
                PreparationTransitionPlanException.Code.INVALID_PHASE);
        assertCode(
                map,
                roster,
                snapshot(MatchPhase.PREPARATION, roster.revision() + 1L, 2, 100L),
                PreparationTransitionPlanException.Code.ROSTER_REVISION_MISMATCH);
        assertCode(
                map,
                roster,
                snapshot(MatchPhase.PREPARATION, roster.revision(), 1, 100L),
                PreparationTransitionPlanException.Code.PLAYER_COUNT_MISMATCH);
    }

    @Test
    void wrapsAllocationFailureWithoutReturningAPartialPlan() {
        LobbyRosterState roster =
                roster(participant("alpha", TeamId.GREEN), participant("bravo", TeamId.BLUE));
        PreparationMapDefinition incomplete =
                map(new PreparationSpawnPoint(2, TeamId.GREEN, 0.0d, 0.0d, 0.0d, 0.0d));

        assertThatThrownBy(
                        () ->
                                PreparationTransitionPlanner.plan(
                                        incomplete, roster, preparation(roster, 1L)))
                .isInstanceOfSatisfying(
                        PreparationTransitionPlanException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(
                                            PreparationTransitionPlanException.Code
                                                    .SPAWN_ALLOCATION_FAILED);
                            assertThat(exception.getCause())
                                    .isInstanceOfSatisfying(
                                            PreparationSpawnAllocationException.class,
                                            cause ->
                                                    assertThat(cause.code())
                                                            .isEqualTo(
                                                                    PreparationSpawnAllocationException
                                                                            .Code
                                                                            .INSUFFICIENT_TEAM_SPAWNS));
                        });
    }

    @Test
    void rejectsMissingInputs() {
        LobbyRosterState roster =
                roster(participant("alpha", TeamId.GREEN), participant("bravo", TeamId.BLUE));
        PreparationMapDefinition map = completeMap();
        LobbyMatchSnapshot preparation = preparation(roster, 1L);

        assertThatThrownBy(() -> PreparationTransitionPlanner.plan(null, roster, preparation))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PreparationTransitionPlanner.plan(map, null, preparation))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PreparationTransitionPlanner.plan(map, roster, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertCode(
            PreparationMapDefinition map,
            LobbyRosterState roster,
            LobbyMatchSnapshot snapshot,
            PreparationTransitionPlanException.Code expected) {
        assertThatThrownBy(() -> PreparationTransitionPlanner.plan(map, roster, snapshot))
                .isInstanceOfSatisfying(
                        PreparationTransitionPlanException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static LobbyRosterState roster(LobbyParticipantState... participants) {
        return new LobbyRosterState(7L, List.of(participants));
    }

    private static LobbyParticipantState participant(String id, TeamId team) {
        return new LobbyParticipantState(
                new LobbyParticipantId(id), Optional.of(team), true);
    }

    private static PreparationMapDefinition completeMap() {
        return map(
                new PreparationSpawnPoint(2, TeamId.GREEN, 0.0d, 0.0d, 0.0d, 0.0d),
                new PreparationSpawnPoint(8, TeamId.BLUE, 10.0d, 0.0d, 0.0d, 90.0d));
    }

    private static PreparationMapDefinition map(PreparationSpawnPoint... spawns) {
        return new PreparationMapDefinition("arena-one", digest(), List.of(spawns));
    }

    private static LobbyMatchSnapshot preparation(LobbyRosterState roster, long roundNumber) {
        return new LobbyMatchSnapshot(
                3L,
                roster.revision(),
                20L,
                MatchPhase.PREPARATION,
                100L,
                roster.participants().size(),
                roundNumber,
                MatchResult.NONE,
                Optional.empty());
    }

    private static LobbyMatchSnapshot snapshot(
            MatchPhase phase, long rosterRevision, int connectedPlayers, long ticksRemaining) {
        return new LobbyMatchSnapshot(
                3L,
                rosterRevision,
                20L,
                phase,
                ticksRemaining,
                connectedPlayers,
                1L,
                MatchResult.NONE,
                Optional.empty());
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationMapDefinition.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }
}
