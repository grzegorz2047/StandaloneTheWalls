package pl.grzegorz2047.standalonethewalls.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;

class MatchLifecycleTest {
    private static final MatchConfiguration FAST =
            new MatchConfiguration(2, 2, 2, 1, 2, 1, 2, 1, 1);

    @Test
    void runsACompleteAcceleratedRoundAndResetsWithoutWallClockTime() {
        MatchState state = MatchState.initial();
        state = accepted(state, new MatchCommand.BeginMapLoad()).state();
        state = accepted(state, new MatchCommand.CompleteMapLoad()).state();
        state = accepted(state, new MatchCommand.UpdateLobbyState(2, true)).state();

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
    void playerCountAloneNeverStartsCountdown() {
        MatchState waiting = waitingState();

        MatchDecision decision = accepted(waiting, new MatchCommand.UpdateLobbyState(4, false));

        assertThat(decision.state().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(decision.state().connectedPlayers()).isEqualTo(4);
        assertThat(decision.events()).isEmpty();
    }

    @Test
    void readyLobbyStartsCountdownExactlyOnce() {
        MatchState waiting = waitingState();
        MatchDecision started = accepted(waiting, new MatchCommand.UpdateLobbyState(2, true));
        MatchDecision repeated =
                accepted(started.state(), new MatchCommand.UpdateLobbyState(2, true));

        assertThat(started.state().phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
        assertThat(started.state().ticksRemaining()).isEqualTo(FAST.startCountdownTicks());
        assertThat(started.events())
                .containsExactly(
                        new MatchEvent.PhaseChanged(
                                MatchPhase.WAITING_FOR_PLAYERS, MatchPhase.START_COUNTDOWN, 1L));
        assertThat(repeated.state()).isEqualTo(started.state());
        assertThat(repeated.events()).isEmpty();
    }

    @Test
    void cancelsCountdownWhenPlayerCountFallsBelowMinimum() {
        MatchState state = waitingState();
        MatchDecision started = accepted(state, new MatchCommand.UpdateLobbyState(2, true));
        MatchDecision cancelled =
                accepted(started.state(), new MatchCommand.UpdateLobbyState(1, false));

        assertThat(cancelled.state().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(cancelled.state().ticksRemaining()).isZero();
        assertThat(cancelled.events())
                .containsExactly(
                        new MatchEvent.CountdownCancelled(
                                CountdownCancellationReason.INSUFFICIENT_PLAYERS, 1, 2),
                        new MatchEvent.PhaseChanged(
                                MatchPhase.START_COUNTDOWN, MatchPhase.WAITING_FOR_PLAYERS, 1L));
    }

    @Test
    void cancelsAndRestartsCountdownWhenLobbyReadinessChanges() {
        MatchState state = waitingState();
        MatchDecision started = accepted(state, new MatchCommand.UpdateLobbyState(3, true));
        MatchDecision elapsed = accepted(started.state(), new MatchCommand.Tick());
        MatchDecision cancelled =
                accepted(elapsed.state(), new MatchCommand.UpdateLobbyState(3, false));
        MatchDecision restarted =
                accepted(cancelled.state(), new MatchCommand.UpdateLobbyState(3, true));

        assertThat(elapsed.state().ticksRemaining()).isOne();
        assertThat(cancelled.state().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(cancelled.events())
                .containsExactly(
                        new MatchEvent.CountdownCancelled(
                                CountdownCancellationReason.LOBBY_NOT_READY, 3, 2),
                        new MatchEvent.PhaseChanged(
                                MatchPhase.START_COUNTDOWN, MatchPhase.WAITING_FOR_PLAYERS, 1L));
        assertThat(restarted.state().phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
        assertThat(restarted.state().ticksRemaining()).isEqualTo(FAST.startCountdownTicks());
    }

    @Test
    void readinessLossAtTheLastCountdownTickCancelsBeforePreparation() {
        MatchState state = waitingState();
        state = accepted(state, new MatchCommand.UpdateLobbyState(2, true)).state();
        state = accepted(state, new MatchCommand.Tick()).state();
        assertThat(state.ticksRemaining()).isOne();

        MatchDecision cancelled = accepted(state, new MatchCommand.UpdateLobbyState(2, false));
        MatchDecision nextTick = accepted(cancelled.state(), new MatchCommand.Tick());

        assertThat(cancelled.state().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(nextTick.state()).isEqualTo(cancelled.state());
        assertThat(nextTick.events()).isEmpty();
    }

    @Test
    void preparationDoesNotRollbackWhenLobbyReadinessLaterChanges() {
        MatchState state = waitingState();
        state = accepted(state, new MatchCommand.UpdateLobbyState(2, true)).state();
        state = accepted(state, new MatchCommand.Tick()).state();
        state = accepted(state, new MatchCommand.Tick()).state();
        assertThat(state.phase()).isEqualTo(MatchPhase.PREPARATION);

        MatchDecision updated = accepted(state, new MatchCommand.UpdateLobbyState(1, false));

        assertThat(updated.state().phase()).isEqualTo(MatchPhase.PREPARATION);
        assertThat(updated.state().connectedPlayers()).isOne();
        assertThat(updated.state().ticksRemaining()).isEqualTo(state.ticksRemaining());
        assertThat(updated.events()).isEmpty();
    }

    @Test
    void rejectsInconsistentReadyLobbyBelowMinimum() {
        MatchState waiting = waitingState();

        MatchDecision decision =
                MatchLifecycle.apply(FAST, waiting, new MatchCommand.UpdateLobbyState(1, true));

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.state()).isEqualTo(waiting);
        assertThat(decision.rejection().orElseThrow().code())
                .isEqualTo(MatchRejection.Code.INVALID_LOBBY_STATE);
    }

    @Test
    void finishesOpenCombatEarlyWithAnExplicitResult() {
        MatchState state = enterPhase(MatchPhase.OPEN_COMBAT);
        MatchDecision decision =
                accepted(state, new MatchCommand.FinishMatch(MatchResult.WINNER_DECLARED));

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
        MatchDecision decision =
                MatchLifecycle.apply(FAST, initial, new MatchCommand.CompleteMapLoad());

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.state()).isEqualTo(initial);
        assertThat(decision.events()).isEmpty();
        assertThat(decision.rejection().orElseThrow().code())
                .isEqualTo(MatchRejection.Code.INVALID_PHASE);
    }

    @Test
    void rejectsNegativePlayerCountsAndEmptyFinishResults() {
        MatchState waiting = waitingState();
        MatchDecision negative =
                MatchLifecycle.apply(FAST, waiting, new MatchCommand.UpdateLobbyState(-1, false));
        MatchState combat = enterPhase(MatchPhase.OPEN_COMBAT);
        MatchDecision noResult =
                MatchLifecycle.apply(FAST, combat, new MatchCommand.FinishMatch(MatchResult.NONE));

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
        state = accepted(state, new MatchCommand.UpdateLobbyState(2, true)).state();
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
        MatchState state =
                accepted(waitingState(), new MatchCommand.UpdateLobbyState(2, true)).state();
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
