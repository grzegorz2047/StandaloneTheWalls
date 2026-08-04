package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;

/** Immutable server-owned assignment of one participant to one exclusive spawn point. */
public record PreparationSpawnAssignment(
        LobbyParticipantId participantId, TeamId team, PreparationSpawnPoint spawnPoint) {
    public PreparationSpawnAssignment {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(spawnPoint, "spawnPoint");
        if (spawnPoint.team() != team) {
            throw new IllegalArgumentException("spawn point team does not match the assignment");
        }
    }
}
