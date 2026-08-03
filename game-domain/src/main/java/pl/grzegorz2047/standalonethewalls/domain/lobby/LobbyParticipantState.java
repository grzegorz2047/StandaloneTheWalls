package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Immutable authoritative lobby state for one authenticated participant. */
public record LobbyParticipantState(
        LobbyParticipantId participantId, Optional<TeamId> team, boolean ready) {
    public LobbyParticipantState {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(team, "team");
        if (ready && team.isEmpty()) {
            throw new IllegalArgumentException("ready participant must have a team");
        }
    }

    public static LobbyParticipantState unassigned(LobbyParticipantId participantId) {
        return new LobbyParticipantState(participantId, Optional.empty(), false);
    }

    public LobbyParticipantState withTeam(TeamId selectedTeam) {
        return new LobbyParticipantState(
                participantId, Optional.of(Objects.requireNonNull(selectedTeam, "selectedTeam")), false);
    }

    public LobbyParticipantState withReady(boolean nextReady) {
        return new LobbyParticipantState(participantId, team, nextReady);
    }
}
