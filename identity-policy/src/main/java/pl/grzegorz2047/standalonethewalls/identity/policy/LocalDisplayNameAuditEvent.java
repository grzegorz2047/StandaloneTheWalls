package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Immutable append-only audit record for one applied display-name mutation. */
public record LocalDisplayNameAuditEvent(
        long sequence,
        Instant occurredAt,
        LocalIdentityAdministratorId administratorId,
        LocalDisplayNameAuditAction action,
        PlayerId playerId,
        Optional<LocalDisplayName> previousDisplayName,
        Optional<LocalDisplayName> newDisplayName,
        LocalHandleAdministrationReason reason) {
    public LocalDisplayNameAuditEvent {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("audit sequence must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        administratorId = Objects.requireNonNull(administratorId, "administratorId");
        action = Objects.requireNonNull(action, "action");
        playerId = Objects.requireNonNull(playerId, "playerId");
        previousDisplayName = Objects.requireNonNull(previousDisplayName, "previousDisplayName");
        newDisplayName = Objects.requireNonNull(newDisplayName, "newDisplayName");
        reason = Objects.requireNonNull(reason, "reason");

        boolean valid =
                switch (action) {
                    case SET ->
                            newDisplayName.isPresent()
                                    && !newDisplayName.equals(previousDisplayName);
                    case CLEAR -> previousDisplayName.isPresent() && newDisplayName.isEmpty();
                };
        if (!valid) {
            throw new IllegalArgumentException("display name audit event shape is invalid");
        }
    }
}
