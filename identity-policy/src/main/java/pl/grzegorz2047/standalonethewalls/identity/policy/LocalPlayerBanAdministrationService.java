package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Validated administration facade above an atomic player-ban-and-audit store. */
public final class LocalPlayerBanAdministrationService {
    private final LocalPlayerBanAdministrationStore store;
    private final Clock clock;

    public LocalPlayerBanAdministrationService(
            LocalPlayerBanAdministrationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalPlayerBanAdministrationResult ban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.ban(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public LocalPlayerBanAdministrationResult unban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason) {
        return store.unban(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(administratorId, "administratorId"),
                Objects.requireNonNull(reason, "reason"),
                clock.instant());
    }

    public Optional<LocalPlayerBan> inspect(PlayerId playerId) {
        return store.findBan(Objects.requireNonNull(playerId, "playerId"));
    }

    public List<LocalPlayerBan> bans() {
        return store.bans();
    }

    public List<LocalPlayerBanAuditEvent> auditEvents() {
        return store.banAuditEvents();
    }
}
