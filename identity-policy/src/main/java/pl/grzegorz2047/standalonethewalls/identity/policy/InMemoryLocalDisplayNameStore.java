package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Thread-safe reference implementation of presentation-only display-name administration. */
public final class InMemoryLocalDisplayNameStore
        implements LocalDisplayNameAdministrationStore {
    public static final int DEFAULT_MAXIMUM_DISPLAY_NAMES = 100_000;
    public static final int DEFAULT_MAXIMUM_AUDIT_EVENTS = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_DISPLAY_NAMES = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_AUDIT_EVENTS = 10_000_000;

    private final Map<PlayerId, LocalDisplayName> displayNames = new HashMap<>();
    private final List<LocalDisplayNameAuditEvent> auditEvents = new ArrayList<>();
    private final int maximumDisplayNames;
    private final int maximumAuditEvents;

    public InMemoryLocalDisplayNameStore() {
        this(DEFAULT_MAXIMUM_DISPLAY_NAMES, DEFAULT_MAXIMUM_AUDIT_EVENTS);
    }

    public InMemoryLocalDisplayNameStore(int maximumDisplayNames, int maximumAuditEvents) {
        if (maximumDisplayNames < 1
                || maximumDisplayNames > ABSOLUTE_MAXIMUM_DISPLAY_NAMES) {
            throw new IllegalArgumentException("maximumDisplayNames is outside the safe range");
        }
        if (maximumAuditEvents < 1
                || maximumAuditEvents > ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        this.maximumDisplayNames = maximumDisplayNames;
        this.maximumAuditEvents = maximumAuditEvents;
    }

    @Override
    public synchronized LocalDisplayNameAdministrationResult setDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalDisplayName displayName,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalDisplayNameExpectation expected =
                Objects.requireNonNull(expectation, "expectation");
        LocalDisplayName replacement = Objects.requireNonNull(displayName, "displayName");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        Optional<LocalDisplayName> current = Optional.ofNullable(displayNames.get(identity));

        if (!expected.matches(current)) {
            return current.isEmpty()
                    ? LocalDisplayNameAdministrationResult.NOT_FOUND
                    : LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH;
        }
        if (current.filter(replacement::equals).isPresent()) {
            return LocalDisplayNameAdministrationResult.UNCHANGED;
        }
        if ((current.isEmpty() && displayNames.size() >= maximumDisplayNames)
                || auditEvents.size() >= maximumAuditEvents) {
            return LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED;
        }

        LocalDisplayNameAuditEvent event =
                new LocalDisplayNameAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalDisplayNameAuditAction.SET,
                        identity,
                        current,
                        Optional.of(replacement),
                        auditReason);
        displayNames.put(identity, replacement);
        auditEvents.add(event);
        return LocalDisplayNameAdministrationResult.APPLIED;
    }

    @Override
    public synchronized LocalDisplayNameAdministrationResult clearDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalDisplayNameExpectation expected =
                Objects.requireNonNull(expectation, "expectation");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        Optional<LocalDisplayName> current = Optional.ofNullable(displayNames.get(identity));

        if (current.isEmpty()) {
            return LocalDisplayNameAdministrationResult.NOT_FOUND;
        }
        if (!expected.matches(current)) {
            return LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH;
        }
        if (auditEvents.size() >= maximumAuditEvents) {
            return LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED;
        }

        LocalDisplayNameAuditEvent event =
                new LocalDisplayNameAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalDisplayNameAuditAction.CLEAR,
                        identity,
                        current,
                        Optional.empty(),
                        auditReason);
        displayNames.remove(identity);
        auditEvents.add(event);
        return LocalDisplayNameAdministrationResult.APPLIED;
    }

    @Override
    public synchronized Optional<LocalDisplayName> find(PlayerId playerId) {
        return Optional.ofNullable(displayNames.get(Objects.requireNonNull(playerId, "playerId")));
    }

    @Override
    public synchronized List<LocalDisplayNameAssignment> displayNames() {
        return displayNames.entrySet().stream()
                .map(entry -> new LocalDisplayNameAssignment(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(assignment -> assignment.playerId().value()))
                .toList();
    }

    @Override
    public synchronized List<LocalDisplayNameAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    private long nextAuditSequence() {
        return auditEvents.size() + 1L;
    }
}
