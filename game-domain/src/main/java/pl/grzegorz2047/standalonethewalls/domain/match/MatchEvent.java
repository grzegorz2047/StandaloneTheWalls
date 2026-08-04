package pl.grzegorz2047.standalonethewalls.domain.match;

import java.util.Objects;

/** Semantic events emitted by accepted lifecycle commands. */
public sealed interface MatchEvent
        permits MatchEvent.PhaseChanged,
                MatchEvent.CountdownCancelled,
                MatchEvent.MatchFinished,
                MatchEvent.RoundReset {

    record PhaseChanged(MatchPhase from, MatchPhase to, long roundNumber) implements MatchEvent {}

    record CountdownCancelled(
            CountdownCancellationReason reason, int connectedPlayers, int minimumPlayers)
            implements MatchEvent {
        public CountdownCancelled {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record MatchFinished(MatchResult result, long roundNumber) implements MatchEvent {}

    record RoundReset(long completedRoundNumber, long nextRoundNumber) implements MatchEvent {}

    enum CountdownCancellationReason {
        INSUFFICIENT_PLAYERS,
        LOBBY_NOT_READY
    }
}
