package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Validated administration facade above an atomic binding-and-audit store. */
public final class LocalHandleAdministrationService {
    private final LocalHandleAdministrationStore store;
    private final Clock clock;

    public LocalHandleAdministrationService(LocalHandleAdministrationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalHandleAdministrationResult reserve(
            CanonicalHandle handle,
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.reserve(
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public LocalHandleAdministrationResult unbind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.unbind(
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(expectedPlayerId, "expectedPlayerId"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public LocalHandleAdministrationResult rebind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.rebind(
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(expectedPlayerId, "expectedPlayerId"),
                Objects.requireNonNull(replacementPlayerId, "replacementPlayerId"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public Optional<PlayerId> inspect(CanonicalHandle handle) {
        return store.find(Objects.requireNonNull(handle, "handle"));
    }

    public List<LocalHandleBinding> bindings() {
        return store.bindings();
    }

    public List<LocalHandleAuditEvent> auditEvents() {
        return store.auditEvents();
    }
}
