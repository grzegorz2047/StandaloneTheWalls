package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Converts one server-owned spawn allocation into its client-specific bounded protocol value. */
public final class PreparationSpawnProtocolAdapter {
    private PreparationSpawnProtocolAdapter() {
        throw new AssertionError("No instances");
    }

    public static pl.grzegorz2047.standalonethewalls.protocol.preparation
                    .PreparationSpawnAssignment
            toProtocol(
                    PreparationMapDefinition map,
                    long rosterRevision,
                    long roundNumber,
                    PreparationSpawnAssignment assignment) {
        PreparationMapDefinition pinnedMap = Objects.requireNonNull(map, "map");
        PreparationSpawnAssignment authoritative =
                Objects.requireNonNull(assignment, "assignment");
        PreparationSpawnPoint spawn = authoritative.spawnPoint();
        return new pl.grzegorz2047.standalonethewalls.protocol.preparation
                .PreparationSpawnAssignment(
                rosterRevision,
                roundNumber,
                pinnedMap.mapId(),
                pinnedMap.mapSha256(),
                protocolTeam(authoritative.team()),
                spawn.index(),
                spawn.x(),
                spawn.y(),
                spawn.z(),
                spawn.yawDegrees());
    }

    private static LobbyTeam protocolTeam(TeamId team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> LobbyTeam.GREEN;
            case BLUE -> LobbyTeam.BLUE;
            case RED -> LobbyTeam.RED;
            case YELLOW -> LobbyTeam.YELLOW;
        };
    }
}
