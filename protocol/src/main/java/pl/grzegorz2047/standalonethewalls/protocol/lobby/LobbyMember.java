package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Public minimal-lobby identity without cryptographic material or internal policy state. */
public record LobbyMember(PlayerId playerId, CanonicalHandle handle) {
    public LobbyMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(handle, "handle");
    }
}
