package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Semantic events emitted only after an authoritative lobby roster state change. */
public sealed interface LobbyRosterEvent
        permits LobbyRosterEvent.ParticipantJoined,
                LobbyRosterEvent.ParticipantLeft,
                LobbyRosterEvent.TeamChanged,
                LobbyRosterEvent.ReadyChanged {

    LobbyParticipantId participantId();

    long revision();

    record ParticipantJoined(LobbyParticipantId participantId, long revision)
            implements LobbyRosterEvent {
        public ParticipantJoined {
            Objects.requireNonNull(participantId, "participantId");
            if (revision < 1L) {
                throw new IllegalArgumentException("event revision must be positive");
            }
        }
    }

    record ParticipantLeft(
            LobbyParticipantId participantId,
            Optional<TeamId> previousTeam,
            boolean wasReady,
            long revision)
            implements LobbyRosterEvent {
        public ParticipantLeft {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(previousTeam, "previousTeam");
            if (revision < 1L) {
                throw new IllegalArgumentException("event revision must be positive");
            }
        }
    }

    record TeamChanged(
            LobbyParticipantId participantId,
            Optional<TeamId> previousTeam,
            TeamId selectedTeam,
            boolean readinessCleared,
            long revision)
            implements LobbyRosterEvent {
        public TeamChanged {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(previousTeam, "previousTeam");
            Objects.requireNonNull(selectedTeam, "selectedTeam");
            if (revision < 1L) {
                throw new IllegalArgumentException("event revision must be positive");
            }
        }
    }

    record ReadyChanged(LobbyParticipantId participantId, boolean ready, long revision)
            implements LobbyRosterEvent {
        public ReadyChanged {
            Objects.requireNonNull(participantId, "participantId");
            if (revision < 1L) {
                throw new IllegalArgumentException("event revision must be positive");
            }
        }
    }
}
