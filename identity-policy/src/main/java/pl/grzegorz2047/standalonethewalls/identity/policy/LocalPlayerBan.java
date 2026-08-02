package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Immutable current local ban of one public player ID. */
public record LocalPlayerBan(
        PlayerId playerId,
        Instant bannedAt,
        LocalIdentityAdministratorId administratorId,
        LocalHandleAdministrationReason reason) {
    public LocalPlayerBan {
        playerId = Objects.requireNonNull(playerId, "playerId");
        bannedAt = Objects.requireNonNull(bannedAt, "bannedAt");
        administratorId = Objects.requireNonNull(administratorId, "administratorId");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
