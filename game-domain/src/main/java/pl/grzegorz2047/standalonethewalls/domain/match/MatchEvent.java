package pl.grzegorz2047.standalonethewalls.domain.match;

/** Semantic events emitted by accepted lifecycle commands. */
public sealed interface MatchEvent
        permits MatchEvent.PhaseChanged,
                MatchEvent.CountdownCancelled,
                MatchEvent.MatchFinished,
                MatchEvent.RoundReset {

    record PhaseChanged(MatchPhase from, MatchPhase to, long roundNumber) implements MatchEvent {}

    record CountdownCancelled(int connectedPlayers, int minimumPlayers) implements MatchEvent {}

    record MatchFinished(MatchResult result, long roundNumber) implements MatchEvent {}

    record RoundReset(long completedRoundNumber, long nextRoundNumber) implements MatchEvent {}
}
