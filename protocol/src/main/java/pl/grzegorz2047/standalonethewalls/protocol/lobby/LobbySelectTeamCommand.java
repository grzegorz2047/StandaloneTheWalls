package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;

/** Client intent to select one concrete canonical team for the authenticated session. */
public record LobbySelectTeamCommand(long requestId, LobbyTeam team) {
    public LobbySelectTeamCommand {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        Objects.requireNonNull(team, "team");
        if (team == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("select-team command requires a concrete team");
        }
    }
}
