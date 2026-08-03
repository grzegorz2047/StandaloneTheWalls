package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Complete structural view of one authoritative connected-lobby snapshot. */
public record ConnectedLobbyModel(
        long revision,
        List<LobbyTeamPanelModel> teamPanels,
        List<LobbyMemberRowModel> unassignedMembers,
        Optional<LobbyMemberRowModel> ownMember,
        LobbyPanelLayout layout) {
    public static final List<LobbyTeam> DISPLAY_TEAM_ORDER =
            List.of(LobbyTeam.RED, LobbyTeam.BLUE, LobbyTeam.GREEN, LobbyTeam.YELLOW);

    public ConnectedLobbyModel {
        if (revision < 1L) {
            throw new IllegalArgumentException("lobby revision must be positive");
        }
        teamPanels = List.copyOf(Objects.requireNonNull(teamPanels, "teamPanels"));
        unassignedMembers =
                List.copyOf(Objects.requireNonNull(unassignedMembers, "unassignedMembers"));
        ownMember = Objects.requireNonNull(ownMember, "ownMember");
        Objects.requireNonNull(layout, "layout");
        validatePanels(teamPanels);
        validateRows(teamPanels, unassignedMembers, ownMember);
    }

    public static ConnectedLobbyModel from(
            LobbySnapshot snapshot, Optional<PlayerId> ownPlayerId, float viewportWidth) {
        LobbySnapshot source = Objects.requireNonNull(snapshot, "snapshot");
        Optional<PlayerId> ownIdentity = Objects.requireNonNull(ownPlayerId, "ownPlayerId");
        Map<LobbyTeam, List<LobbyMemberRowModel>> grouped = new EnumMap<>(LobbyTeam.class);
        for (LobbyTeam team : LobbyTeam.values()) {
            grouped.put(team, new ArrayList<>());
        }

        LobbyMemberRowModel ownRow = null;
        for (LobbyMember member : source.members()) {
            boolean own = ownIdentity.map(member.playerId()::equals).orElse(false);
            LobbyMemberRowModel row = LobbyMemberRowModel.from(member, own);
            grouped.get(member.team()).add(row);
            if (own) {
                ownRow = row;
            }
        }
        if (ownIdentity.isPresent() && ownRow == null) {
            throw new IllegalArgumentException("authoritative lobby snapshot does not contain self");
        }

        List<LobbyTeamPanelModel> panels =
                DISPLAY_TEAM_ORDER.stream()
                        .map(team -> new LobbyTeamPanelModel(team, grouped.get(team)))
                        .toList();
        return new ConnectedLobbyModel(
                source.revision(),
                panels,
                grouped.get(LobbyTeam.UNASSIGNED),
                Optional.ofNullable(ownRow),
                LobbyPanelLayout.forViewportWidth(viewportWidth));
    }

    public int totalMembers() {
        int assigned = teamPanels.stream().mapToInt(LobbyTeamPanelModel::occupiedSlots).sum();
        return assigned + unassignedMembers.size();
    }

    public LobbyTeamPanelModel panel(LobbyTeam team) {
        LobbyTeam requested = Objects.requireNonNull(team, "team");
        if (requested == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("unassigned does not have a team panel");
        }
        return teamPanels.stream()
                .filter(panel -> panel.team() == requested)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("team panel is missing"));
    }

    private static void validatePanels(List<LobbyTeamPanelModel> panels) {
        if (panels.size() != DISPLAY_TEAM_ORDER.size()) {
            throw new IllegalArgumentException("connected lobby requires exactly four team panels");
        }
        List<LobbyTeam> actualOrder = panels.stream().map(LobbyTeamPanelModel::team).toList();
        if (!actualOrder.equals(DISPLAY_TEAM_ORDER)) {
            throw new IllegalArgumentException("team panels are not in the stable display order");
        }
    }

    private static void validateRows(
            List<LobbyTeamPanelModel> panels,
            List<LobbyMemberRowModel> unassigned,
            Optional<LobbyMemberRowModel> ownMember) {
        Set<PlayerId> playerIds = new HashSet<>();
        int ownCount = 0;
        for (LobbyTeamPanelModel panel : panels) {
            for (LobbyMemberRowModel member : panel.members()) {
                if (!playerIds.add(member.playerId())) {
                    throw new IllegalArgumentException("connected lobby contains duplicate playerId");
                }
                if (member.ownPlayer()) {
                    ownCount++;
                }
            }
        }
        for (LobbyMemberRowModel member : unassigned) {
            Objects.requireNonNull(member, "unassigned member");
            if (member.team() != LobbyTeam.UNASSIGNED) {
                throw new IllegalArgumentException("unassigned list contains an assigned member");
            }
            if (!playerIds.add(member.playerId())) {
                throw new IllegalArgumentException("connected lobby contains duplicate playerId");
            }
            if (member.ownPlayer()) {
                ownCount++;
            }
        }
        if (playerIds.size() > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new IllegalArgumentException("connected lobby exceeds protocol capacity");
        }
        if (ownCount > 1 || ownMember.isPresent() != (ownCount == 1)) {
            throw new IllegalArgumentException("connected lobby own-player marker is inconsistent");
        }
        ownMember.ifPresent(
                own -> {
                    if (!own.ownPlayer() || !playerIds.contains(own.playerId())) {
                        throw new IllegalArgumentException(
                                "connected lobby own member is not one of its rows");
                    }
                });
    }
}
