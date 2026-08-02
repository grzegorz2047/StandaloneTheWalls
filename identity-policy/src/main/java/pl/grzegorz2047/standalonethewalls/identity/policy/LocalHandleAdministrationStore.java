package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Atomic persistence boundary for local bindings and their administrative audit trail. */
public interface LocalHandleAdministrationStore extends LocalHandleBindingStore {
    LocalHandleAdministrationResult reserve(
            CanonicalHandle handle,
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    LocalHandleAdministrationResult unbind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    LocalHandleAdministrationResult rebind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    Optional<PlayerId> find(CanonicalHandle handle);

    List<LocalHandleBinding> bindings();

    List<LocalHandleAuditEvent> auditEvents();
}
