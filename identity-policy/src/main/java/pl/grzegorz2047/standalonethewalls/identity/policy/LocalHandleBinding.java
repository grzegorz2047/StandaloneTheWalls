package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Immutable public view of one local canonical-handle binding. */
public record LocalHandleBinding(CanonicalHandle handle, PlayerId playerId) {
    public LocalHandleBinding {
        handle = Objects.requireNonNull(handle, "handle");
        playerId = Objects.requireNonNull(playerId, "playerId");
    }
}
