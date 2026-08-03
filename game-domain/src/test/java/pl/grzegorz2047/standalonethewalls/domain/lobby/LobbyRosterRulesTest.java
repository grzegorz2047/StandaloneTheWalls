package pl.grzegorz2047.standalonethewalls.domain.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

class LobbyRosterRulesTest {
    private static final LobbyConfiguration STANDARD = LobbyConfiguration.standard();
    private static final LobbyParticipantId ALICE = id("player-alice");
    private static final LobbyParticipantId BOB = id("player-bob");
    private static final LobbyParticipantId CAROL = id("player-carol");
    private static final LobbyParticipantId DAVE = id("player-dave");

    @Test
    void joinsAndLeavesInCanonicalOrderWithMonotonicRevisions() {
        LobbyRosterState state = LobbyRosterState.initial();
        state = accepted(state, new LobbyRosterCommand.Join(BOB)).state();
        LobbyRosterDecision joinedAlice = accepted(state, new LobbyRosterCommand.Join(ALICE));

        assertThat(joinedAlice.state().revision()).isEqualTo(2L);
        assertThat(joinedAlice.state().participants())
                .extracting(LobbyParticipantState::participantId)
                .containsExactly(ALICE, BOB);
        assertThat(joinedAlice.events())
                .containsExactly(new LobbyRosterEvent.ParticipantJoined(ALICE, 2L));

        LobbyRosterDecision left =
                accepted(joinedAlice.state(), new LobbyRosterCommand.Leave(BOB));
        assertThat(left.state().revision()).isEqualTo(3L);
        assertThat(left.state().participants())
                .extracting(LobbyParticipantState::participantId)
                .containsExactly(ALICE);
        assertThat(left.events())
                .containsExactly(
                        new LobbyRosterEvent.ParticipantLeft(
                                BOB, Optional.empty(), false, 3L));
    }

    @Test
    void rejectsDuplicateUnknownAndOverCapacityCommandsWithoutChangingState() {
        LobbyConfiguration twoPlayers =
                new LobbyConfiguration(Set.of(TeamId.GREEN, TeamId.BLUE), 2, 1, 2);
        LobbyRosterState state = LobbyRosterState.initial();
        state = accepted(twoPlayers, state, new LobbyRosterCommand.Join(ALICE)).state();

        assertRejected(
                twoPlayers,
                state,
                new LobbyRosterCommand.Join(ALICE),
                LobbyRosterRejection.DUPLICATE_PARTICIPANT);
        assertRejected(
                twoPlayers,
                state,
                new LobbyRosterCommand.Leave(BOB),
                LobbyRosterRejection.UNKNOWN_PARTICIPANT);

        state = accepted(twoPlayers, state, new LobbyRosterCommand.Join(BOB)).state();
        assertRejected(
                twoPlayers,
                state,
                new LobbyRosterCommand.Join(CAROL),
                LobbyRosterRejection.LOBBY_FULL);
    }

    @Test
    void acceptsOnlyTeamsThatAchieveTheBestAvailableBalance() {
        LobbyRosterState state = joinAll(STANDARD, ALICE, BOB, CAROL, DAVE);
        state = select(state, ALICE, TeamId.GREEN);

        assertRejected(
                STANDARD,
                state,
                new LobbyRosterCommand.SelectTeam(BOB, TeamId.GREEN),
                LobbyRosterRejection.TEAM_IMBALANCE);
        state = select(state, BOB, TeamId.BLUE);

        assertRejected(
                STANDARD,
                state,
                new LobbyRosterCommand.SelectTeam(CAROL, TeamId.BLUE),
                LobbyRosterRejection.TEAM_IMBALANCE);
        state = select(state, CAROL, TeamId.RED);
        state = select(state, DAVE, TeamId.YELLOW);

        assertThat(state.teamSizes(STANDARD))
                .containsEntry(TeamId.GREEN, 1)
                .containsEntry(TeamId.BLUE, 1)
                .containsEntry(TeamId.RED, 1)
                .containsEntry(TeamId.YELLOW, 1);
    }

    @Test
    void rejectsDisabledAndFullTeams() {
        LobbyConfiguration twoTeams =
                new LobbyConfiguration(Set.of(TeamId.GREEN, TeamId.BLUE), 2, 1, 2);
        LobbyRosterState state = joinAll(twoTeams, ALICE, BOB);

        assertRejected(
                twoTeams,
                state,
                new LobbyRosterCommand.SelectTeam(ALICE, TeamId.RED),
                LobbyRosterRejection.TEAM_DISABLED);
        state = select(twoTeams, state, ALICE, TeamId.GREEN);
        assertRejected(
                twoTeams,
                state,
                new LobbyRosterCommand.SelectTeam(BOB, TeamId.GREEN),
                LobbyRosterRejection.TEAM_FULL);
    }

    @Test
    void requiresATeamForReadyAndClearsReadyWhenTeamChanges() {
        LobbyRosterState state = joinAll(STANDARD, ALICE, BOB);
        assertRejected(
                STANDARD,
                state,
                new LobbyRosterCommand.SetReady(ALICE, true),
                LobbyRosterRejection.TEAM_REQUIRED);

        state = select(state, ALICE, TeamId.GREEN);
        state = select(state, BOB, TeamId.BLUE);
        LobbyRosterDecision ready =
                accepted(state, new LobbyRosterCommand.SetReady(ALICE, true));
        assertThat(ready.state().participant(ALICE).orElseThrow().ready()).isTrue();
        assertThat(ready.events())
                .containsExactly(new LobbyRosterEvent.ReadyChanged(ALICE, true, 5L));

        LobbyRosterDecision moved =
                accepted(
                        ready.state(),
                        new LobbyRosterCommand.SelectTeam(ALICE, TeamId.RED));
        LobbyParticipantState alice = moved.state().participant(ALICE).orElseThrow();
        assertThat(alice.team()).contains(TeamId.RED);
        assertThat(alice.ready()).isFalse();
        assertThat(moved.events())
                .containsExactly(
                        new LobbyRosterEvent.TeamChanged(
                                ALICE, Optional.of(TeamId.GREEN), TeamId.RED, true, 6L));
    }

    @Test
    void idempotentCommandsDoNotAdvanceRevisionOrEmitEvents() {
        LobbyRosterState state = joinAll(STANDARD, ALICE, BOB);
        state = select(state, ALICE, TeamId.GREEN);
        state = select(state, BOB, TeamId.BLUE);

        LobbyRosterDecision sameTeam =
                accepted(state, new LobbyRosterCommand.SelectTeam(ALICE, TeamId.GREEN));
        assertThat(sameTeam.state()).isSameAs(state);
        assertThat(sameTeam.events()).isEmpty();

        LobbyRosterDecision alreadyNotReady =
                accepted(state, new LobbyRosterCommand.SetReady(ALICE, false));
        assertThat(alreadyNotReady.state()).isSameAs(state);
        assertThat(alreadyNotReady.events()).isEmpty();

        LobbyRosterState readyState =
                accepted(state, new LobbyRosterCommand.SetReady(ALICE, true)).state();
        LobbyRosterDecision alreadyReady =
                accepted(readyState, new LobbyRosterCommand.SetReady(ALICE, true));
        assertThat(alreadyReady.state()).isSameAs(readyState);
        assertThat(alreadyReady.events()).isEmpty();
    }

    @Test
    void derivesReadyToStartOnlyForEnoughReadyPlayersAcrossTwoTeams() {
        LobbyRosterState state = joinAll(STANDARD, ALICE, BOB);
        state = select(state, ALICE, TeamId.GREEN);
        state = select(state, BOB, TeamId.BLUE);

        assertThat(state.readyToStart(STANDARD)).isFalse();
        state = accepted(state, new LobbyRosterCommand.SetReady(ALICE, true)).state();
        assertThat(state.readyToStart(STANDARD)).isFalse();
        state = accepted(state, new LobbyRosterCommand.SetReady(BOB, true)).state();

        assertThat(state.readyCount()).isEqualTo(2);
        assertThat(state.readyToStart(STANDARD)).isTrue();

        LobbyRosterState oneTeam =
                new LobbyRosterState(
                        8L,
                        List.of(
                                new LobbyParticipantState(
                                        ALICE, Optional.of(TeamId.GREEN), true),
                                new LobbyParticipantState(
                                        BOB, Optional.of(TeamId.GREEN), true)));
        assertThat(oneTeam.readyToStart(STANDARD)).isFalse();
    }

    @Test
    void validatesCanonicalStateAndConfigurationCompatibility() {
        assertThatThrownBy(
                        () ->
                                new LobbyRosterState(
                                        0L,
                                        List.of(
                                                LobbyParticipantState.unassigned(BOB),
                                                LobbyParticipantState.unassigned(ALICE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly ordered");
        assertThatThrownBy(
                        () ->
                                new LobbyRosterState(
                                        0L,
                                        List.of(
                                                LobbyParticipantState.unassigned(ALICE),
                                                LobbyParticipantState.unassigned(ALICE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");

        LobbyConfiguration twoTeams =
                new LobbyConfiguration(EnumSet.of(TeamId.GREEN, TeamId.BLUE), 2, 1, 2);
        LobbyRosterState disabledTeamState =
                new LobbyRosterState(
                        1L,
                        List.of(
                                new LobbyParticipantState(
                                        ALICE, Optional.of(TeamId.RED), false)));
        assertThatThrownBy(
                        () ->
                                LobbyRosterRules.apply(
                                        twoTeams,
                                        disabledTeamState,
                                        new LobbyRosterCommand.SetReady(ALICE, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled team");
    }

    private static LobbyRosterState joinAll(
            LobbyConfiguration configuration, LobbyParticipantId... participantIds) {
        LobbyRosterState state = LobbyRosterState.initial();
        for (LobbyParticipantId participantId : participantIds) {
            state =
                    accepted(configuration, state, new LobbyRosterCommand.Join(participantId))
                            .state();
        }
        return state;
    }

    private static LobbyRosterState select(
            LobbyRosterState state, LobbyParticipantId participantId, TeamId team) {
        return select(STANDARD, state, participantId, team);
    }

    private static LobbyRosterState select(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyParticipantId participantId,
            TeamId team) {
        return accepted(
                        configuration,
                        state,
                        new LobbyRosterCommand.SelectTeam(participantId, team))
                .state();
    }

    private static LobbyRosterDecision accepted(
            LobbyRosterState state, LobbyRosterCommand command) {
        return accepted(STANDARD, state, command);
    }

    private static LobbyRosterDecision accepted(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyRosterCommand command) {
        LobbyRosterDecision decision = LobbyRosterRules.apply(configuration, state, command);
        assertThat(decision.accepted()).isTrue();
        assertThat(decision.rejection()).isEmpty();
        return decision;
    }

    private static void assertRejected(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyRosterCommand command,
            LobbyRosterRejection expected) {
        LobbyRosterDecision decision = LobbyRosterRules.apply(configuration, state, command);
        assertThat(decision.accepted()).isFalse();
        assertThat(decision.state()).isSameAs(state);
        assertThat(decision.events()).isEmpty();
        assertThat(decision.rejection()).contains(expected);
    }

    private static LobbyParticipantId id(String value) {
        return new LobbyParticipantId(value);
    }
}
