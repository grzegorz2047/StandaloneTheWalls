package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Fail-closed player admission check performed before canonical-handle authorization. */
public final class PlayerBanAdmissionService {
    private final LocalPlayerBanStore bans;

    public PlayerBanAdmissionService(LocalPlayerBanStore bans) {
        this.bans = Objects.requireNonNull(bans, "bans");
    }

    public PlayerBanAdmissionDecision evaluate(PlayerId playerId) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        return bans.findBan(identity).isPresent()
                ? PlayerBanAdmissionDecision.PLAYER_BANNED
                : PlayerBanAdmissionDecision.ALLOWED;
    }
}
