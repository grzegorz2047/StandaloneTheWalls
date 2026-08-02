package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Read boundary for local bans keyed only by the stable public player ID. */
public interface LocalPlayerBanStore {
    Optional<LocalPlayerBan> findBan(PlayerId playerId);
}
