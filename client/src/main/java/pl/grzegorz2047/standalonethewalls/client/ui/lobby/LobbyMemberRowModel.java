package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Renderer-independent public row for one authoritative lobby participant. */
public record LobbyMemberRowModel(
        PlayerId playerId, String handle, LobbyTeam team, boolean ready, boolean ownPlayer) {
    public LobbyMemberRowModel {
        Objects.requireNonNull(playerId, "playerId");
        handle = Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(team, "team");
        if (handle.isBlank()) {
            throw new IllegalArgumentException("lobby member handle must not be blank");
        }
        if (ready && team == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("unassigned lobby member cannot be ready");
        }
    }

    public static LobbyMemberRowModel from(LobbyMember member, boolean ownPlayer) {
        LobbyMember source = Objects.requireNonNull(member, "member");
        return new LobbyMemberRowModel(
                source.playerId(),
                source.handle().value(),
                source.team(),
                source.ready(),
                ownPlayer);
    }
}
