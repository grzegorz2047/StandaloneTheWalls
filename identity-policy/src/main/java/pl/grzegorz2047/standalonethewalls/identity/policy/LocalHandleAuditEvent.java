package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** One immutable, monotonically sequenced administrative binding mutation. */
public record LocalHandleAuditEvent(
        long sequence,
        Instant occurredAt,
        LocalIdentityAdministratorId administratorId,
        LocalHandleAuditAction action,
        CanonicalHandle handle,
        Optional<PlayerId> previousPlayerId,
        Optional<PlayerId> newPlayerId,
        LocalHandleAdministrationReason reason) {
    public LocalHandleAuditEvent {
        if (sequence < 1L) {
            throw new IllegalArgumentException("audit sequence must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        administratorId = Objects.requireNonNull(administratorId, "administratorId");
        action = Objects.requireNonNull(action, "action");
        handle = Objects.requireNonNull(handle, "handle");
        previousPlayerId = Objects.requireNonNull(previousPlayerId, "previousPlayerId");
        newPlayerId = Objects.requireNonNull(newPlayerId, "newPlayerId");
        reason = Objects.requireNonNull(reason, "reason");
        requireShape(action, previousPlayerId, newPlayerId);
    }

    private static void requireShape(
            LocalHandleAuditAction action,
            Optional<PlayerId> previousPlayerId,
            Optional<PlayerId> newPlayerId) {
        switch (action) {
            case RESERVE -> {
                if (previousPlayerId.isPresent() || newPlayerId.isEmpty()) {
                    throw new IllegalArgumentException("reserve audit event has an invalid shape");
                }
            }
            case UNBIND -> {
                if (previousPlayerId.isEmpty() || newPlayerId.isPresent()) {
                    throw new IllegalArgumentException("unbind audit event has an invalid shape");
                }
            }
            case REBIND -> {
                if (previousPlayerId.isEmpty()
                        || newPlayerId.isEmpty()
                        || previousPlayerId.equals(newPlayerId)) {
                    throw new IllegalArgumentException("rebind audit event has an invalid shape");
                }
            }
        }
    }
}
