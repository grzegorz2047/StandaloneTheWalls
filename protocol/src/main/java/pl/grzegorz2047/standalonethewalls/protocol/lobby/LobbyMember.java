package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Public lobby identity and authoritative team/readiness state without secret material. */
public record LobbyMember(
        PlayerId playerId, CanonicalHandle handle, LobbyTeam team, boolean ready) {
    public LobbyMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(team, "team");
        if (ready && team == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("ready lobby member must have a team");
        }
    }

    public LobbyMember(PlayerId playerId, CanonicalHandle handle) {
        this(playerId, handle, LobbyTeam.UNASSIGNED, false);
    }
}
