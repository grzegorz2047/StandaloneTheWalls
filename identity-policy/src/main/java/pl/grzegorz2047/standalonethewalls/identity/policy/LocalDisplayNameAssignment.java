package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** One local presentation-only display name assigned to a stable player identity. */
public record LocalDisplayNameAssignment(PlayerId playerId, LocalDisplayName displayName) {
    public LocalDisplayNameAssignment {
        playerId = Objects.requireNonNull(playerId, "playerId");
        displayName = Objects.requireNonNull(displayName, "displayName");
    }
}
