package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Canonically ordered authoritative lobby roster with a monotonic visible revision. */
public record LobbyRosterState(long revision, List<LobbyParticipantState> participants) {
    public LobbyRosterState {
        if (revision < 0L) {
            throw new IllegalArgumentException("lobby roster revision cannot be negative");
        }
        Objects.requireNonNull(participants, "participants");
        if (participants.size() > LobbyConfiguration.MAXIMUM_SUPPORTED_PLAYERS) {
            throw new IllegalArgumentException("lobby roster exceeds the supported capacity");
        }
        List<LobbyParticipantState> copy = List.copyOf(participants);
        LobbyParticipantId previous = null;
        for (LobbyParticipantState participant : copy) {
            Objects.requireNonNull(participant, "participant");
            if (previous != null && previous.compareTo(participant.participantId()) >= 0) {
                throw new IllegalArgumentException(
                        "lobby participants must be unique and strictly ordered by id");
            }
            previous = participant.participantId();
        }
        participants = copy;
    }

    public static LobbyRosterState initial() {
        return new LobbyRosterState(0L, List.of());
    }

    public Optional<LobbyParticipantState> participant(LobbyParticipantId participantId) {
        Objects.requireNonNull(participantId, "participantId");
        return participants.stream()
                .filter(participant -> participant.participantId().equals(participantId))
                .findFirst();
    }

    public int teamSize(TeamId team) {
        Objects.requireNonNull(team, "team");
        return Math.toIntExact(
                participants.stream().filter(participant -> participant.team().filter(team::equals).isPresent()).count());
    }

    public Map<TeamId, Integer> teamSizes(LobbyConfiguration configuration) {
        LobbyConfiguration rules = Objects.requireNonNull(configuration, "configuration");
        EnumMap<TeamId, Integer> sizes = new EnumMap<>(TeamId.class);
        for (TeamId team : rules.enabledTeamsInOrder()) {
            sizes.put(team, teamSize(team));
        }
        return Collections.unmodifiableMap(sizes);
    }

    public int readyCount() {
        return Math.toIntExact(participants.stream().filter(LobbyParticipantState::ready).count());
    }

    public boolean readyToStart(LobbyConfiguration configuration) {
        LobbyConfiguration rules = Objects.requireNonNull(configuration, "configuration");
        if (participants.size() < rules.minimumReadyPlayers()) {
            return false;
        }
        EnumSet<TeamId> representedTeams = EnumSet.noneOf(TeamId.class);
        for (LobbyParticipantState participant : participants) {
            if (!participant.ready() || participant.team().isEmpty()) {
                return false;
            }
            TeamId team = participant.team().orElseThrow();
            if (!rules.enabledTeams().contains(team)) {
                return false;
            }
            representedTeams.add(team);
        }
        return representedTeams.size() >= 2;
    }
}
