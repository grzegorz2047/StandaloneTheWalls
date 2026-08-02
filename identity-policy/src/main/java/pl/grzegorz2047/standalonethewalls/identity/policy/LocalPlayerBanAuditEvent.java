package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** One immutable, monotonically sequenced local player-ban mutation. */
public record LocalPlayerBanAuditEvent(
        long sequence,
        Instant occurredAt,
        LocalIdentityAdministratorId administratorId,
        LocalPlayerBanAuditAction action,
        PlayerId playerId,
        LocalHandleAdministrationReason reason) {
    public LocalPlayerBanAuditEvent {
        if (sequence < 1L) {
            throw new IllegalArgumentException("player ban audit sequence must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        administratorId = Objects.requireNonNull(administratorId, "administratorId");
        action = Objects.requireNonNull(action, "action");
        playerId = Objects.requireNonNull(playerId, "playerId");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
