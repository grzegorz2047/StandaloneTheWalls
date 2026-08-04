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

class PreparationSpawnAllocatorTest {
    @Test
    void assignsCanonicalParticipantsToSortedExclusiveTeamSpawns() {
        LobbyRosterState roster =
                roster(
                        assigned("alpha", TeamId.GREEN),
                        assigned("bravo", TeamId.GREEN),
                        assigned("charlie", TeamId.BLUE));
        PreparationSpawnPoint greenSecond = spawn(8, TeamId.GREEN, 8.0d);
        PreparationSpawnPoint blueFirst = spawn(3, TeamId.BLUE, 30.0d);
        PreparationSpawnPoint greenFirst = spawn(2, TeamId.GREEN, 2.0d);
        PreparationSpawnPoint unused = spawn(20, TeamId.YELLOW, 200.0d);

        List<PreparationSpawnAssignment> assignments =
                PreparationSpawnAllocator.allocate(
                        roster, List.of(greenSecond, unused, blueFirst, greenFirst));

        assertThat(assignments)
                .extracting(
                        assignment -> assignment.participantId().value(),
                        assignment -> assignment.team(),
                        assignment -> assignment.spawnPoint().index())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alpha", TeamId.GREEN, 2),
                        org.assertj.core.groups.Tuple.tuple("bravo", TeamId.GREEN, 8),
                        org.assertj.core.groups.Tuple.tuple("charlie", TeamId.BLUE, 3));
        assertThatThrownBy(() -> assignments.add(assignments.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnEmptyAuthoritativeRoster() {
        assertFailure(
                () -> PreparationSpawnAllocator.allocate(LobbyRosterState.initial(), List.of()),
                PreparationSpawnAllocationException.Code.EMPTY_ROSTER);
    }

    @Test
    void rejectsAnUnassignedParticipantInsteadOfGuessingATeam() {
        LobbyRosterState roster =
                roster(LobbyParticipantState.unassigned(new LobbyParticipantId("alpha")));

        assertFailure(
                () ->
                        PreparationSpawnAllocator.allocate(
                                roster, List.of(spawn(1, TeamId.GREEN, 1.0d))),
                PreparationSpawnAllocationException.Code.UNASSIGNED_PARTICIPANT);
    }

    @Test
    void rejectsDuplicateSpawnIndicesAcrossDifferentTeams() {
        LobbyRosterState roster =
                roster(assigned("alpha", TeamId.GREEN), assigned("bravo", TeamId.BLUE));

        assertFailure(
                () ->
                        PreparationSpawnAllocator.allocate(
                                roster,
                                List.of(
                                        spawn(1, TeamId.GREEN, 1.0d),
                                        spawn(1, TeamId.BLUE, 20.0d))),
                PreparationSpawnAllocationException.Code.DUPLICATE_SPAWN_INDEX);
    }

    @Test
    void rejectsATeamWithoutEnoughExclusiveSpawns() {
        LobbyRosterState roster =
                roster(
                        assigned("alpha", TeamId.GREEN),
                        assigned("bravo", TeamId.GREEN),
                        assigned("charlie", TeamId.BLUE));

        assertFailure(
                () ->
                        PreparationSpawnAllocator.allocate(
                                roster,
                                List.of(
                                        spawn(1, TeamId.GREEN, 1.0d),
                                        spawn(2, TeamId.BLUE, 20.0d))),
                PreparationSpawnAllocationException.Code.INSUFFICIENT_TEAM_SPAWNS);
    }

    @Test
    void rejectsNonFiniteCoordinatesAndUnboundedYaw() {
        assertThatThrownBy(
                        () ->
                                new PreparationSpawnPoint(
                                        1, TeamId.GREEN, Double.NaN, 0.0d, 0.0d, 0.0d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PreparationSpawnPoint(1, TeamId.GREEN, 0.0d, 0.0d, 0.0d, 180.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static LobbyRosterState roster(LobbyParticipantState... participants) {
        return new LobbyRosterState(1L, List.of(participants));
    }

    private static LobbyParticipantState assigned(String id, TeamId team) {
        return new LobbyParticipantState(new LobbyParticipantId(id), Optional.of(team), true);
    }

    private static PreparationSpawnPoint spawn(int index, TeamId team, double x) {
        return new PreparationSpawnPoint(index, team, x, 1.0d, -x, 0.0d);
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            PreparationSpawnAllocationException.Code code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        PreparationSpawnAllocationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
