package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Thread-safe ephemeral local binding and administration store. */
public final class InMemoryLocalHandleBindingStore implements LocalHandleAdministrationStore {
    public static final int DEFAULT_MAXIMUM_BINDINGS = 100_000;
    public static final int DEFAULT_MAXIMUM_AUDIT_EVENTS = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_BINDINGS = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_AUDIT_EVENTS = 10_000_000;

    private final Map<CanonicalHandle, PlayerId> bindings = new HashMap<>();
    private final List<LocalHandleAuditEvent> auditEvents = new ArrayList<>();
    private final int maximumBindings;
    private final int maximumAuditEvents;

    public InMemoryLocalHandleBindingStore() {
        this(DEFAULT_MAXIMUM_BINDINGS, DEFAULT_MAXIMUM_AUDIT_EVENTS);
    }

    public InMemoryLocalHandleBindingStore(int maximumBindings, int maximumAuditEvents) {
        if (maximumBindings < 1 || maximumBindings > ABSOLUTE_MAXIMUM_BINDINGS) {
            throw new IllegalArgumentException("maximumBindings is outside the safe range");
        }
        if (maximumAuditEvents < 1 || maximumAuditEvents > ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        this.maximumBindings = maximumBindings;
        this.maximumAuditEvents = maximumAuditEvents;
    }

    @Override
    public synchronized LocalHandleBindingResult bindOrVerify(
            CanonicalHandle handle, PlayerId playerId) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        PlayerId existing = bindings.get(canonicalHandle);
        if (existing != null) {
            return existing.equals(identity)
                    ? LocalHandleBindingResult.MATCHED
                    : LocalHandleBindingResult.CONFLICT;
        }
        if (bindings.size() >= maximumBindings) {
            return LocalHandleBindingResult.CAPACITY_EXCEEDED;
        }
        bindings.put(canonicalHandle, identity);
        return LocalHandleBindingResult.BOUND;
    }

    @Override
    public synchronized LocalHandleAdministrationResult reserve(
            CanonicalHandle handle,
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        PlayerId existing = bindings.get(canonicalHandle);
        if (existing != null) {
            return existing.equals(identity)
                    ? LocalHandleAdministrationResult.ALREADY_MATCHED
                    : LocalHandleAdministrationResult.CONFLICT;
        }
        if (!canAddBindingAndAudit()) {
            return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
        }
        LocalHandleAuditEvent event =
                new LocalHandleAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalHandleAuditAction.RESERVE,
                        canonicalHandle,
                        Optional.empty(),
                        Optional.of(identity),
                        auditReason);
        bindings.put(canonicalHandle, identity);
        auditEvents.add(event);
        return LocalHandleAdministrationResult.RESERVED;
    }

    @Override
    public synchronized LocalHandleAdministrationResult unbind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId expected = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        PlayerId existing = bindings.get(canonicalHandle);
        if (existing == null) {
            return LocalHandleAdministrationResult.NOT_FOUND;
        }
        if (!existing.equals(expected)) {
            return LocalHandleAdministrationResult.EXPECTATION_MISMATCH;
        }
        if (!canAddAudit()) {
            return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
        }
        LocalHandleAuditEvent event =
                new LocalHandleAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalHandleAuditAction.UNBIND,
                        canonicalHandle,
                        Optional.of(existing),
                        Optional.empty(),
                        auditReason);
        bindings.remove(canonicalHandle);
        auditEvents.add(event);
        return LocalHandleAdministrationResult.UNBOUND;
    }

    @Override
    public synchronized LocalHandleAdministrationResult rebind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId expected = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        PlayerId replacement =
                Objects.requireNonNull(replacementPlayerId, "replacementPlayerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        PlayerId existing = bindings.get(canonicalHandle);
        if (existing == null) {
            return LocalHandleAdministrationResult.NOT_FOUND;
        }
        if (!existing.equals(expected)) {
            return LocalHandleAdministrationResult.EXPECTATION_MISMATCH;
        }
        if (existing.equals(replacement)) {
            return LocalHandleAdministrationResult.SAME_PLAYER;
        }
        if (!canAddAudit()) {
            return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
        }
        LocalHandleAuditEvent event =
                new LocalHandleAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalHandleAuditAction.REBIND,
                        canonicalHandle,
                        Optional.of(existing),
                        Optional.of(replacement),
                        auditReason);
        bindings.put(canonicalHandle, replacement);
        auditEvents.add(event);
        return LocalHandleAdministrationResult.REBOUND;
    }

    @Override
    public synchronized Optional<PlayerId> find(CanonicalHandle handle) {
        return Optional.ofNullable(bindings.get(Objects.requireNonNull(handle, "handle")));
    }

    @Override
    public synchronized List<LocalHandleBinding> bindings() {
        return bindings.entrySet().stream()
                .map(entry -> new LocalHandleBinding(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(binding -> binding.handle().value()))
                .toList();
    }

    @Override
    public synchronized List<LocalHandleAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    public synchronized int size() {
        return bindings.size();
    }

    private boolean canAddBindingAndAudit() {
        return bindings.size() < maximumBindings && canAddAudit();
    }

    private boolean canAddAudit() {
        return auditEvents.size() < maximumAuditEvents;
    }

    private long nextAuditSequence() {
        return auditEvents.size() + 1L;
    }
}
