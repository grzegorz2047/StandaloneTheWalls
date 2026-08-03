package pl.grzegorz2047.standalonethewalls.domain.lobby;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** Stateless deterministic transition rules for team selection and lobby readiness. */
public final class LobbyRosterRules {
    private LobbyRosterRules() {
        throw new AssertionError("No instances");
    }

    public static LobbyRosterDecision apply(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyRosterCommand command) {
        LobbyConfiguration rules = Objects.requireNonNull(configuration, "configuration");
        LobbyRosterState current = Objects.requireNonNull(state, "state");
        LobbyRosterCommand requested = Objects.requireNonNull(command, "command");
        requireCompatibleState(rules, current);

        return switch (requested) {
            case LobbyRosterCommand.Join join -> applyJoin(rules, current, join);
            case LobbyRosterCommand.Leave leave -> applyLeave(current, leave);
            case LobbyRosterCommand.SelectTeam selectTeam ->
                    applySelectTeam(rules, current, selectTeam);
            case LobbyRosterCommand.SetReady setReady -> applySetReady(current, setReady);
        };
    }

    private static LobbyRosterDecision applyJoin(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyRosterCommand.Join command) {
        TreeMap<LobbyParticipantId, LobbyParticipantState> participants = participantsById(state);
        if (participants.containsKey(command.participantId())) {
            return LobbyRosterDecision.rejected(
                    state, LobbyRosterRejection.DUPLICATE_PARTICIPANT);
        }
        if (participants.size() >= configuration.maximumPlayers()) {
            return LobbyRosterDecision.rejected(state, LobbyRosterRejection.LOBBY_FULL);
        }

        long revision = nextRevision(state);
        participants.put(
                command.participantId(),
                LobbyParticipantState.unassigned(command.participantId()));
        LobbyRosterState next = state(revision, participants);
        return LobbyRosterDecision.accepted(
                next,
                List.of(new LobbyRosterEvent.ParticipantJoined(command.participantId(), revision)));
    }

    private static LobbyRosterDecision applyLeave(
            LobbyRosterState state, LobbyRosterCommand.Leave command) {
        TreeMap<LobbyParticipantId, LobbyParticipantState> participants = participantsById(state);
        LobbyParticipantState removed = participants.remove(command.participantId());
        if (removed == null) {
            return LobbyRosterDecision.rejected(
                    state, LobbyRosterRejection.UNKNOWN_PARTICIPANT);
        }

        long revision = nextRevision(state);
        LobbyRosterState next = state(revision, participants);
        return LobbyRosterDecision.accepted(
                next,
                List.of(
                        new LobbyRosterEvent.ParticipantLeft(
                                removed.participantId(),
                                removed.team(),
                                removed.ready(),
                                revision)));
    }

    private static LobbyRosterDecision applySelectTeam(
            LobbyConfiguration configuration,
            LobbyRosterState state,
            LobbyRosterCommand.SelectTeam command) {
        TreeMap<LobbyParticipantId, LobbyParticipantState> participants = participantsById(state);
        LobbyParticipantState participant = participants.get(command.participantId());
        if (participant == null) {
            return LobbyRosterDecision.rejected(
                    state, LobbyRosterRejection.UNKNOWN_PARTICIPANT);
        }
        if (!configuration.enabledTeams().contains(command.team())) {
            return LobbyRosterDecision.rejected(state, LobbyRosterRejection.TEAM_DISABLED);
        }
        if (participant.team().filter(command.team()::equals).isPresent()) {
            return LobbyRosterDecision.accepted(state, List.of());
        }

        EnumMap<TeamId, Integer> baseSizes = teamSizes(configuration, state);
        participant.team().ifPresent(team -> baseSizes.compute(team, (ignored, size) -> size - 1));
        if (baseSizes.get(command.team()) >= configuration.maximumTeamSize()) {
            return LobbyRosterDecision.rejected(state, LobbyRosterRejection.TEAM_FULL);
        }

        int requestedSpread = spreadAfterJoining(configuration, baseSizes, command.team());
        int bestAvailableSpread = Integer.MAX_VALUE;
        for (TeamId candidate : configuration.enabledTeamsInOrder()) {
            if (baseSizes.get(candidate) < configuration.maximumTeamSize()) {
                bestAvailableSpread =
                        Math.min(
                                bestAvailableSpread,
                                spreadAfterJoining(configuration, baseSizes, candidate));
            }
        }
        if (requestedSpread != bestAvailableSpread) {
            return LobbyRosterDecision.rejected(state, LobbyRosterRejection.TEAM_IMBALANCE);
        }

        long revision = nextRevision(state);
        Optional<TeamId> previousTeam = participant.team();
        boolean readinessCleared = participant.ready();
        participants.put(command.participantId(), participant.withTeam(command.team()));
        LobbyRosterState next = state(revision, participants);
        return LobbyRosterDecision.accepted(
                next,
                List.of(
                        new LobbyRosterEvent.TeamChanged(
                                command.participantId(),
                                previousTeam,
                                command.team(),
                                readinessCleared,
                                revision)));
    }

    private static LobbyRosterDecision applySetReady(
            LobbyRosterState state, LobbyRosterCommand.SetReady command) {
        TreeMap<LobbyParticipantId, LobbyParticipantState> participants = participantsById(state);
        LobbyParticipantState participant = participants.get(command.participantId());
        if (participant == null) {
            return LobbyRosterDecision.rejected(
                    state, LobbyRosterRejection.UNKNOWN_PARTICIPANT);
        }
        if (command.ready() && participant.team().isEmpty()) {
            return LobbyRosterDecision.rejected(state, LobbyRosterRejection.TEAM_REQUIRED);
        }
        if (participant.ready() == command.ready()) {
            return LobbyRosterDecision.accepted(state, List.of());
        }

        long revision = nextRevision(state);
        participants.put(command.participantId(), participant.withReady(command.ready()));
        LobbyRosterState next = state(revision, participants);
        return LobbyRosterDecision.accepted(
                next,
                List.of(
                        new LobbyRosterEvent.ReadyChanged(
                                command.participantId(), command.ready(), revision)));
    }

    private static void requireCompatibleState(
            LobbyConfiguration configuration, LobbyRosterState state) {
        if (state.participants().size() > configuration.maximumPlayers()) {
            throw new IllegalArgumentException("lobby state exceeds configured maximumPlayers");
        }
        EnumMap<TeamId, Integer> sizes = new EnumMap<>(TeamId.class);
        for (TeamId team : configuration.enabledTeamsInOrder()) {
            sizes.put(team, 0);
        }
        for (LobbyParticipantState participant : state.participants()) {
            if (participant.team().isEmpty()) {
                continue;
            }
            TeamId team = participant.team().orElseThrow();
            if (!configuration.enabledTeams().contains(team)) {
                throw new IllegalArgumentException("lobby state contains a disabled team");
            }
            int size = sizes.compute(team, (ignored, current) -> current + 1);
            if (size > configuration.maximumTeamSize()) {
                throw new IllegalArgumentException("lobby state exceeds configured team capacity");
            }
        }
    }

    private static EnumMap<TeamId, Integer> teamSizes(
            LobbyConfiguration configuration, LobbyRosterState state) {
        EnumMap<TeamId, Integer> sizes = new EnumMap<>(TeamId.class);
        for (TeamId team : configuration.enabledTeamsInOrder()) {
            sizes.put(team, state.teamSize(team));
        }
        return sizes;
    }

    private static int spreadAfterJoining(
            LobbyConfiguration configuration, Map<TeamId, Integer> baseSizes, TeamId selectedTeam) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (TeamId team : configuration.enabledTeamsInOrder()) {
            int size = baseSizes.get(team) + (team == selectedTeam ? 1 : 0);
            minimum = Math.min(minimum, size);
            maximum = Math.max(maximum, size);
        }
        return maximum - minimum;
    }

    private static TreeMap<LobbyParticipantId, LobbyParticipantState> participantsById(
            LobbyRosterState state) {
        TreeMap<LobbyParticipantId, LobbyParticipantState> participants = new TreeMap<>();
        for (LobbyParticipantState participant : state.participants()) {
            participants.put(participant.participantId(), participant);
        }
        return participants;
    }

    private static LobbyRosterState state(
            long revision, Map<LobbyParticipantId, LobbyParticipantState> participants) {
        return new LobbyRosterState(revision, new ArrayList<>(participants.values()));
    }

    private static long nextRevision(LobbyRosterState state) {
        return Math.incrementExact(state.revision());
    }
}
