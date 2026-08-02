package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Validation and time-source facade above an atomic display-name store. */
public final class LocalDisplayNameAdministrationService {
    private final LocalDisplayNameAdministrationStore store;
    private final Clock clock;

    public LocalDisplayNameAdministrationService(
            LocalDisplayNameAdministrationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalDisplayNameAdministrationResult setDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            String displayName,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        if (displayName == null) {
            return LocalDisplayNameAdministrationResult.INVALID_VALUE;
        }
        LocalDisplayName validated;
        try {
            validated = new LocalDisplayName(displayName);
        } catch (IllegalArgumentException exception) {
            return LocalDisplayNameAdministrationResult.INVALID_VALUE;
        }
        return store.setDisplayName(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(expectation, "expectation"),
                validated,
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public LocalDisplayNameAdministrationResult clearDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.clearDisplayName(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(expectation, "expectation"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public Optional<LocalDisplayName> inspect(PlayerId playerId) {
        return store.find(Objects.requireNonNull(playerId, "playerId"));
    }

    public List<LocalDisplayNameAssignment> displayNames() {
        return store.displayNames();
    }

    public List<LocalDisplayNameAuditEvent> auditEvents() {
        return store.auditEvents();
    }
}
