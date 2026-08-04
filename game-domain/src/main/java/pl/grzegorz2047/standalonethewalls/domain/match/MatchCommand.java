package pl.grzegorz2047.standalonethewalls.domain.match;

/** Commands accepted by the deterministic match lifecycle. */
public sealed interface MatchCommand
        permits MatchCommand.BeginMapLoad,
                MatchCommand.CompleteMapLoad,
                MatchCommand.UpdateLobbyState,
                MatchCommand.Tick,
                MatchCommand.FinishMatch {

    record BeginMapLoad() implements MatchCommand {}

    record CompleteMapLoad() implements MatchCommand {}

    record UpdateLobbyState(int connectedPlayers, boolean readyToStart) implements MatchCommand {}

    record Tick() implements MatchCommand {}

    record FinishMatch(MatchResult result) implements MatchCommand {}
}
