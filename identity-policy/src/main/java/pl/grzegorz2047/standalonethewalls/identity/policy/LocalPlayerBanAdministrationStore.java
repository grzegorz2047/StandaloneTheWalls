package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.time.Instant;
import java.util.List;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Atomic persistence boundary for local player bans and their audit trail. */
public interface LocalPlayerBanAdministrationStore extends LocalPlayerBanStore {
    LocalPlayerBanAdministrationResult ban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    LocalPlayerBanAdministrationResult unban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt);

    List<LocalPlayerBan> bans();

    List<LocalPlayerBanAuditEvent> banAuditEvents();
}
