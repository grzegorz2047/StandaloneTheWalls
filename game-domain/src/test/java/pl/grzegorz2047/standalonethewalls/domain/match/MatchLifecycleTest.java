package pl.grzegorz2047.standalonethewalls.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchLifecycleTest {
    private static final MatchConfiguration FAST =
            new MatchConfiguration(2, 2, 2, 1, 2, 1, 2, 1, 1);

    @Test
    void runsACompleteAcceleratedRoundAndResetsWithoutWallClockTime() {
        MatchState state = MatchState.initial();
        state = accepted(state, new MatchCommand.BeginMapLoad()).state();
        state = accepted(state, new MatchCommand.CompleteMapLoad()).state();
        state = accepted(state, new MatchCommand.UpdatePlayerCount(2)).state();

        List<MatchPhase> phases = new ArrayList<>();
        phases.add(state.phase());
        while (state.roundNumber() == 1L) {
            MatchDecision decision = accepted(state, new MatchCommand.Tick());
            state = decision.state();
            decision.events().stream()
                    .filter(MatchEvent.PhaseChanged.class::isInstance)
                    .map(MatchEvent.PhaseChanged.class::cast)
                    .map(MatchEvent.PhaseChanged::to)
                    .forEach(phases::add);
        }

        assertThat(phases)
                .containsExactly(
                        MatchPhase.START_COUNTDOWN,
                        MatchPhase.PREPARATION,
                        MatchPhase.WALLS_OPENING,
                        MatchPhase.OPEN_COMBAT,
                        MatchPhase.DEATHMATCH_TRANSITION,
                        MatchPhase.DEATHMATCH,
                        MatchPhase.RESULTS,
                        MatchPhase.RESETTING,
                        MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(state.phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(state.roundNumber()).isEqualTo(2L);
        assertThat(state.connectedPlayers()).isEqualTo(2);
        assertThat(state.result()).isEqualTo(MatchResult.NONE);
    }

    @Test
    void cancelsCountdownWhenPlayerCountFallsBelowMinimum() {
        MatchState state = waitingState();
        MatchDecision started = accepted(state, new MatchCommand.UpdatePlayerCount(2));
        MatchDecision cancelled = accepted(started.state(), new MatchCommand.UpdatePlayerCount(1));

        assertThat(cancelled.state().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(cancelled.state().ticksRemaining()).isZero();
        assertThat(cancelled.events())
                .containsExactly(
                        new MatchEvent.CountdownCancelled(1, 2),
                        new MatchEvent.PhaseChanged(
                                MatchPhase.START_COUNTDOWN,
                                MatchPhase.WAITING_FOR_PLAYERS,
                                1L));
    }

    @Test
    void finishesOpenCombatEarlyWithAnExplicitResult() {
        MatchState state = enterPhase(MatchPhase.OPEN_COMBAT);
        MatchDecision decision = accepted(
                state, new MatchCommand.FinishMatch(MatchResult.WINNER_DECLARED));

        assertThat(decision.state().phase()).isEqualTo(MatchPhase.RESULTS);
        assertThat(decision.state().result()).isEqualTo(MatchResult.WINNER_DECLARED);
        assertThat(decision.state().ticksRemaining()).isEqualTo(FAST.resultsTicks());
        assertThat(decision.events())
                .containsExactly(
                        new MatchEvent.MatchFinished(MatchResult.WINNER_DECLARED, 1L),
                        new MatchEvent.PhaseChanged(
                                MatchPhase.OPEN_COMBAT, MatchPhase.RESULTS, 1L));
    }

    @Test
    void rejectsCommandsFromInvalidPhasesWithoutChangingState() {
        MatchState initial = MatchState.initial();
        MatchDecision decision = MatchLifecycle.apply(
                FAST, initial, new MatchCommand.CompleteMapLoad());

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.state()).isEqualTo(initial);
        assertThat(decision.events()).isEmpty();
        assertThat(decision.rejection().orElseThrow().code())
                .isEqualTo(MatchRejection.Code.INVALID_PHASE);
    }

    @Test
    void rejectsNegativePlayerCountsAndEmptyFinishResults() {
        MatchState waiting = waitingState();
        MatchDecision negative = MatchLifecycle.apply(
                FAST, waiting, new MatchCommand.UpdatePlayerCount(-1));
        MatchState combat = enterPhase(MatchPhase.OPEN_COMBAT);
        MatchDecision noResult = MatchLifecycle.apply(
                FAST, combat, new MatchCommand.FinishMatch(MatchResult.NONE));

        assertThat(negative.rejection().orElseThrow().code())
                .isEqualTo(MatchRejection.Code.INVALID_PLAYER_COUNT);
        assertThat(negative.state()).isEqualTo(waiting);
        assertThat(noResult.rejection().orElseThrow().code())
                .isEqualTo(MatchRejection.Code.INVALID_RESULT);
        assertThat(noResult.state()).isEqualTo(combat);
    }

    @Test
    void phaseOrderNeverRegressesBeforeTheExplicitRoundReset() {
        MatchState state = waitingState();
        state = accepted(state, new MatchCommand.UpdatePlayerCount(2)).state();
        int previousOrdinal = state.phase().ordinal();

        while (state.phase() != MatchPhase.RESETTING) {
            state = accepted(state, new MatchCommand.Tick()).state();
            assertThat(state.phase().ordinal()).isGreaterThanOrEqualTo(previousOrdinal);
            previousOrdinal = state.phase().ordinal();
        }
    }

    private static MatchState waitingState() {
        MatchState state = MatchState.initial();
        state = accepted(state, new MatchCommand.BeginMapLoad()).state();
        return accepted(state, new MatchCommand.CompleteMapLoad()).state();
    }

    private static MatchState enterPhase(MatchPhase target) {
        MatchState state = accepted(
                        waitingState(), new MatchCommand.UpdatePlayerCount(2))
                .state();
        int guard = 100;
        while (state.phase() != target && guard-- > 0) {
            state = accepted(state, new MatchCommand.Tick()).state();
        }
        assertThat(state.phase()).isEqualTo(target);
        return state;
    }

    private static MatchDecision accepted(MatchState state, MatchCommand command) {
        MatchDecision decision = MatchLifecycle.apply(FAST, state, command);
        assertThat(decision.rejection()).isEmpty();
        return decision;
    }
}
