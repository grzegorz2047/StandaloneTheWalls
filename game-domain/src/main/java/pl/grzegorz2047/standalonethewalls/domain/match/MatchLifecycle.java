package pl.grzegorz2047.standalonethewalls.domain.match;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;

/** Stateless transition function for the match lifecycle. */
public final class MatchLifecycle {
    private MatchLifecycle() {
        throw new AssertionError("No instances");
    }

    public static MatchDecision apply(
            MatchConfiguration configuration, MatchState state, MatchCommand command) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");

        return switch (command) {
            case MatchCommand.BeginMapLoad ignored -> beginMapLoad(state);
            case MatchCommand.CompleteMapLoad ignored -> completeMapLoad(state);
            case MatchCommand.UpdateLobbyState update ->
                    updateLobbyState(
                            configuration,
                            state,
                            update.connectedPlayers(),
                            update.readyToStart());
            case MatchCommand.Tick ignored -> tick(configuration, state);
            case MatchCommand.FinishMatch finish ->
                    finishMatch(configuration, state, finish.result());
        };
    }

    private static MatchDecision beginMapLoad(MatchState state) {
        if (state.phase() != MatchPhase.BOOT) {
            return invalidPhase(state, "map loading can begin only from BOOT");
        }
        return transition(state, MatchPhase.LOADING_MAP, 0L, MatchResult.NONE);
    }

    private static MatchDecision completeMapLoad(MatchState state) {
        if (state.phase() != MatchPhase.LOADING_MAP) {
            return invalidPhase(state, "map loading can complete only from LOADING_MAP");
        }
        return transition(state, MatchPhase.WAITING_FOR_PLAYERS, 0L, MatchResult.NONE);
    }

    private static MatchDecision updateLobbyState(
            MatchConfiguration configuration,
            MatchState state,
            int connectedPlayers,
            boolean readyToStart) {
        if (connectedPlayers < 0) {
            return MatchDecision.rejected(
                    state,
                    new MatchRejection(
                            MatchRejection.Code.INVALID_PLAYER_COUNT,
                            "connectedPlayers cannot be negative"));
        }
        if (readyToStart && connectedPlayers < configuration.minimumPlayers()) {
            return MatchDecision.rejected(
                    state,
                    new MatchRejection(
                            MatchRejection.Code.INVALID_LOBBY_STATE,
                            "readyToStart requires the configured minimum player count"));
        }

        MatchState updated =
                new MatchState(
                        state.phase(),
                        state.ticksRemaining(),
                        connectedPlayers,
                        state.roundNumber(),
                        state.result());

        if (state.phase() == MatchPhase.WAITING_FOR_PLAYERS && readyToStart) {
            return transition(
                    updated,
                    MatchPhase.START_COUNTDOWN,
                    configuration.startCountdownTicks(),
                    MatchResult.NONE);
        }

        if (state.phase() == MatchPhase.START_COUNTDOWN && !readyToStart) {
            MatchState waiting =
                    new MatchState(
                            MatchPhase.WAITING_FOR_PLAYERS,
                            0L,
                            connectedPlayers,
                            state.roundNumber(),
                            MatchResult.NONE);
            CountdownCancellationReason reason =
                    connectedPlayers < configuration.minimumPlayers()
                            ? CountdownCancellationReason.INSUFFICIENT_PLAYERS
                            : CountdownCancellationReason.LOBBY_NOT_READY;
            return MatchDecision.accepted(
                    waiting,
                    new MatchEvent.CountdownCancelled(
                            reason, connectedPlayers, configuration.minimumPlayers()),
                    new MatchEvent.PhaseChanged(
                            MatchPhase.START_COUNTDOWN,
                            MatchPhase.WAITING_FOR_PLAYERS,
                            state.roundNumber()));
        }

        return MatchDecision.accepted(updated);
    }

    private static MatchDecision tick(MatchConfiguration configuration, MatchState state) {
        if (state.ticksRemaining() == 0L) {
            return MatchDecision.accepted(state);
        }

        if (state.ticksRemaining() > 1L) {
            return MatchDecision.accepted(
                    new MatchState(
                            state.phase(),
                            state.ticksRemaining() - 1L,
                            state.connectedPlayers(),
                            state.roundNumber(),
                            state.result()));
        }

        return switch (state.phase()) {
            case START_COUNTDOWN ->
                    enterTimed(configuration, state, MatchPhase.PREPARATION, MatchResult.NONE);
            case PREPARATION ->
                    enterTimed(configuration, state, MatchPhase.WALLS_OPENING, MatchResult.NONE);
            case WALLS_OPENING ->
                    enterTimed(configuration, state, MatchPhase.OPEN_COMBAT, MatchResult.NONE);
            case OPEN_COMBAT ->
                    enterTimed(
                            configuration,
                            state,
                            MatchPhase.DEATHMATCH_TRANSITION,
                            MatchResult.NONE);
            case DEATHMATCH_TRANSITION ->
                    enterTimed(configuration, state, MatchPhase.DEATHMATCH, MatchResult.NONE);
            case DEATHMATCH -> finishMatch(configuration, state, MatchResult.TECHNICAL_DRAW);
            case RESULTS -> enterTimed(configuration, state, MatchPhase.RESETTING, state.result());
            case RESETTING -> resetRound(state);
            case BOOT, LOADING_MAP, WAITING_FOR_PLAYERS ->
                    throw new IllegalStateException("untimed phase cannot expire");
        };
    }

    private static MatchDecision finishMatch(
            MatchConfiguration configuration, MatchState state, MatchResult result) {
        if (result == null || result == MatchResult.NONE) {
            return MatchDecision.rejected(
                    state,
                    new MatchRejection(
                            MatchRejection.Code.INVALID_RESULT,
                            "finish result must be WINNER_DECLARED or TECHNICAL_DRAW"));
        }
        boolean finishable =
                state.phase() == MatchPhase.OPEN_COMBAT
                        || state.phase() == MatchPhase.DEATHMATCH_TRANSITION
                        || state.phase() == MatchPhase.DEATHMATCH;
        if (!finishable) {
            return invalidPhase(state, "match cannot finish from " + state.phase());
        }

        MatchState results =
                new MatchState(
                        MatchPhase.RESULTS,
                        configuration.resultsTicks(),
                        state.connectedPlayers(),
                        state.roundNumber(),
                        result);
        return MatchDecision.accepted(
                results,
                new MatchEvent.MatchFinished(result, state.roundNumber()),
                new MatchEvent.PhaseChanged(
                        state.phase(), MatchPhase.RESULTS, state.roundNumber()));
    }

    private static MatchDecision resetRound(MatchState state) {
        long nextRound = Math.addExact(state.roundNumber(), 1L);
        MatchState waiting =
                new MatchState(
                        MatchPhase.WAITING_FOR_PLAYERS,
                        0L,
                        state.connectedPlayers(),
                        nextRound,
                        MatchResult.NONE);
        return MatchDecision.accepted(
                waiting,
                new MatchEvent.RoundReset(state.roundNumber(), nextRound),
                new MatchEvent.PhaseChanged(
                        MatchPhase.RESETTING, MatchPhase.WAITING_FOR_PLAYERS, nextRound));
    }

    private static MatchDecision enterTimed(
            MatchConfiguration configuration,
            MatchState state,
            MatchPhase nextPhase,
            MatchResult result) {
        return transition(state, nextPhase, configuration.durationFor(nextPhase), result);
    }

    private static MatchDecision transition(
            MatchState state, MatchPhase nextPhase, long ticks, MatchResult result) {
        MatchState next =
                new MatchState(
                        nextPhase, ticks, state.connectedPlayers(), state.roundNumber(), result);
        return MatchDecision.accepted(
                next, new MatchEvent.PhaseChanged(state.phase(), nextPhase, state.roundNumber()));
    }

    private static MatchDecision invalidPhase(MatchState state, String detail) {
        return MatchDecision.rejected(
                state, new MatchRejection(MatchRejection.Code.INVALID_PHASE, detail));
    }
}
