package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** One of the four visible team panels with authoritative occupied rows. */
public record LobbyTeamPanelModel(LobbyTeam team, List<LobbyMemberRowModel> members) {
    public LobbyTeamPanelModel {
        Objects.requireNonNull(team, "team");
        if (team == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("unassigned members do not form a team panel");
        }
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        for (LobbyMemberRowModel member : members) {
            LobbyMemberRowModel row = Objects.requireNonNull(member, "member");
            if (row.team() != team) {
                throw new IllegalArgumentException("team panel contains a member from another team");
            }
        }
    }

    public int occupiedSlots() {
        return members.size();
    }
}
