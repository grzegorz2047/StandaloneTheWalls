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

/** Thread-safe ephemeral player-ban and audit store. */
public final class InMemoryLocalPlayerBanStore implements LocalPlayerBanAdministrationStore {
    public static final int DEFAULT_MAXIMUM_BANS = 100_000;
    public static final int DEFAULT_MAXIMUM_AUDIT_EVENTS = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_BANS = 1_000_000;
    public static final int ABSOLUTE_MAXIMUM_AUDIT_EVENTS = 10_000_000;

    private final Map<PlayerId, LocalPlayerBan> bans = new HashMap<>();
    private final List<LocalPlayerBanAuditEvent> auditEvents = new ArrayList<>();
    private final int maximumBans;
    private final int maximumAuditEvents;

    public InMemoryLocalPlayerBanStore() {
        this(DEFAULT_MAXIMUM_BANS, DEFAULT_MAXIMUM_AUDIT_EVENTS);
    }

    public InMemoryLocalPlayerBanStore(int maximumBans, int maximumAuditEvents) {
        if (maximumBans < 1 || maximumBans > ABSOLUTE_MAXIMUM_BANS) {
            throw new IllegalArgumentException("maximumBans is outside the safe range");
        }
        if (maximumAuditEvents < 1 || maximumAuditEvents > ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        this.maximumBans = maximumBans;
        this.maximumAuditEvents = maximumAuditEvents;
    }

    @Override
    public synchronized LocalPlayerBanAdministrationResult ban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        if (bans.containsKey(identity)) {
            return LocalPlayerBanAdministrationResult.ALREADY_BANNED;
        }
        if (bans.size() >= maximumBans || auditEvents.size() >= maximumAuditEvents) {
            return LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED;
        }
        LocalPlayerBan ban = new LocalPlayerBan(identity, timestamp, administrator, auditReason);
        LocalPlayerBanAuditEvent event =
                new LocalPlayerBanAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalPlayerBanAuditAction.BAN,
                        identity,
                        auditReason);
        bans.put(identity, ban);
        auditEvents.add(event);
        return LocalPlayerBanAdministrationResult.BANNED;
    }

    @Override
    public synchronized LocalPlayerBanAdministrationResult unban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        if (!bans.containsKey(identity)) {
            return LocalPlayerBanAdministrationResult.NOT_BANNED;
        }
        if (auditEvents.size() >= maximumAuditEvents) {
            return LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED;
        }
        LocalPlayerBanAuditEvent event =
                new LocalPlayerBanAuditEvent(
                        nextAuditSequence(),
                        timestamp,
                        administrator,
                        LocalPlayerBanAuditAction.UNBAN,
                        identity,
                        auditReason);
        bans.remove(identity);
        auditEvents.add(event);
        return LocalPlayerBanAdministrationResult.UNBANNED;
    }

    @Override
    public synchronized Optional<LocalPlayerBan> findBan(PlayerId playerId) {
        return Optional.ofNullable(bans.get(Objects.requireNonNull(playerId, "playerId")));
    }

    @Override
    public synchronized List<LocalPlayerBan> bans() {
        return bans.values().stream()
                .sorted(Comparator.comparing(ban -> ban.playerId().value()))
                .toList();
    }

    @Override
    public synchronized List<LocalPlayerBanAuditEvent> banAuditEvents() {
        return List.copyOf(auditEvents);
    }

    private long nextAuditSequence() {
        return auditEvents.size() + 1L;
    }
}
