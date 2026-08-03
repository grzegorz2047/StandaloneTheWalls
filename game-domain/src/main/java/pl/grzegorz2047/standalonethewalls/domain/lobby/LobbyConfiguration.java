package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Bounded configuration for deterministic team selection and lobby readiness. */
public record LobbyConfiguration(
        Set<TeamId> enabledTeams,
        int maximumPlayers,
        int maximumTeamSize,
        int minimumReadyPlayers) {
    public static final int MAXIMUM_SUPPORTED_PLAYERS = 40;

    public LobbyConfiguration {
        Objects.requireNonNull(enabledTeams, "enabledTeams");
        EnumSet<TeamId> teams = EnumSet.noneOf(TeamId.class);
        for (TeamId team : enabledTeams) {
            teams.add(Objects.requireNonNull(team, "enabled team"));
        }
        if (teams.size() < 2) {
            throw new IllegalArgumentException("at least two teams must be enabled");
        }
        if (maximumPlayers < 2 || maximumPlayers > MAXIMUM_SUPPORTED_PLAYERS) {
            throw new IllegalArgumentException("maximumPlayers is outside the supported range");
        }
        if (maximumTeamSize < 1 || maximumTeamSize > MAXIMUM_SUPPORTED_PLAYERS) {
            throw new IllegalArgumentException("maximumTeamSize is outside the supported range");
        }
        if (Math.multiplyExact(teams.size(), maximumTeamSize) < maximumPlayers) {
            throw new IllegalArgumentException("enabled teams cannot hold maximumPlayers");
        }
        if (minimumReadyPlayers < 2 || minimumReadyPlayers > maximumPlayers) {
            throw new IllegalArgumentException(
                    "minimumReadyPlayers is outside the supported range");
        }
        enabledTeams = Collections.unmodifiableSet(teams);
    }

    public static LobbyConfiguration standard() {
        return new LobbyConfiguration(EnumSet.allOf(TeamId.class), 40, 10, 2);
    }

    public List<TeamId> enabledTeamsInOrder() {
        return List.copyOf(enabledTeams);
    }
}
