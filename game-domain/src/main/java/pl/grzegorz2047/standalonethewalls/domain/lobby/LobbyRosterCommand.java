package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Commands accepted by the deterministic authoritative lobby roster. */
public sealed interface LobbyRosterCommand
        permits LobbyRosterCommand.Join,
                LobbyRosterCommand.Leave,
                LobbyRosterCommand.SelectTeam,
                LobbyRosterCommand.SetReady {

    LobbyParticipantId participantId();

    record Join(LobbyParticipantId participantId) implements LobbyRosterCommand {
        public Join {
            Objects.requireNonNull(participantId, "participantId");
        }
    }

    record Leave(LobbyParticipantId participantId) implements LobbyRosterCommand {
        public Leave {
            Objects.requireNonNull(participantId, "participantId");
        }
    }

    record SelectTeam(LobbyParticipantId participantId, TeamId team) implements LobbyRosterCommand {
        public SelectTeam {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(team, "team");
        }
    }

    record SetReady(LobbyParticipantId participantId, boolean ready) implements LobbyRosterCommand {
        public SetReady {
            Objects.requireNonNull(participantId, "participantId");
        }
    }
}
