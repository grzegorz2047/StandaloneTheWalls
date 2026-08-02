package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Atomic presentation-name state and audit port keyed only by stable player ID. */
public interface LocalDisplayNameAdministrationStore {
    LocalDisplayNameAdministrationResult setDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalDisplayName displayName,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    LocalDisplayNameAdministrationResult clearDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    Optional<LocalDisplayName> find(PlayerId playerId);

    List<LocalDisplayNameAssignment> displayNames();

    List<LocalDisplayNameAuditEvent> auditEvents();
}
