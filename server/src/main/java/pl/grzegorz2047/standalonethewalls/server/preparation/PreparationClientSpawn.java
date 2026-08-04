package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;

/** One participant-targeted protocol assignment ready for ordered reliable delivery. */
public record PreparationClientSpawn(
        LobbyParticipantId participantId,
        pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment
                assignment) {
    public PreparationClientSpawn {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(assignment, "assignment");
    }
}
