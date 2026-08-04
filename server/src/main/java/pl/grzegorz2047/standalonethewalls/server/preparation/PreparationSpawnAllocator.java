package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;

/** Deterministically assigns authoritative lobby participants to exclusive team spawn points. */
public final class PreparationSpawnAllocator {
    private PreparationSpawnAllocator() {}

    public static List<PreparationSpawnAssignment> allocate(
            LobbyRosterState roster, List<PreparationSpawnPoint> spawnPoints) {
        LobbyRosterState authoritativeRoster = Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(spawnPoints, "spawnPoints");
        if (authoritativeRoster.participants().isEmpty()) {
            throw failure(
                    PreparationSpawnAllocationException.Code.EMPTY_ROSTER,
                    "preparation spawn allocation requires at least one participant");
        }

        Map<TeamId, List<LobbyParticipantState>> participantsByTeam = new EnumMap<>(TeamId.class);
        for (LobbyParticipantState participant : authoritativeRoster.participants()) {
            TeamId team =
                    participant
                            .team()
                            .orElseThrow(
                                    () ->
                                            failure(
                                                    PreparationSpawnAllocationException.Code
                                                            .UNASSIGNED_PARTICIPANT,
                                                    "preparation participant has no authoritative team"));
            participantsByTeam.computeIfAbsent(team, ignored -> new ArrayList<>()).add(participant);
        }

        Map<TeamId, List<PreparationSpawnPoint>> spawnsByTeam = new EnumMap<>(TeamId.class);
        Set<Integer> usedIndices = new HashSet<>();
        for (PreparationSpawnPoint spawnPoint : List.copyOf(spawnPoints)) {
            PreparationSpawnPoint candidate = Objects.requireNonNull(spawnPoint, "spawnPoint");
            if (!usedIndices.add(candidate.index())) {
                throw failure(
                        PreparationSpawnAllocationException.Code.DUPLICATE_SPAWN_INDEX,
                        "preparation spawn indices must be globally unique");
            }
            spawnsByTeam
                    .computeIfAbsent(candidate.team(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        spawnsByTeam
                .values()
                .forEach(
                        teamSpawns ->
                                teamSpawns.sort(
                                        Comparator.comparingInt(PreparationSpawnPoint::index)));

        List<PreparationSpawnAssignment> assignments =
                new ArrayList<>(authoritativeRoster.participants().size());
        for (TeamId team : TeamId.values()) {
            List<LobbyParticipantState> teamParticipants =
                    participantsByTeam.getOrDefault(team, List.of());
            if (teamParticipants.isEmpty()) {
                continue;
            }
            List<PreparationSpawnPoint> teamSpawns = spawnsByTeam.getOrDefault(team, List.of());
            if (teamSpawns.size() < teamParticipants.size()) {
                throw failure(
                        PreparationSpawnAllocationException.Code.INSUFFICIENT_TEAM_SPAWNS,
                        "preparation map does not contain enough exclusive spawns for one team");
            }
            for (int index = 0; index < teamParticipants.size(); index++) {
                LobbyParticipantState participant = teamParticipants.get(index);
                assignments.add(
                        new PreparationSpawnAssignment(
                                participant.participantId(), team, teamSpawns.get(index)));
            }
        }
        assignments.sort(Comparator.comparing(PreparationSpawnAssignment::participantId));
        return List.copyOf(assignments);
    }

    private static PreparationSpawnAllocationException failure(
            PreparationSpawnAllocationException.Code code, String message) {
        return new PreparationSpawnAllocationException(code, message);
    }
}
