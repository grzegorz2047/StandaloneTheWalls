package pl.grzegorz2047.standalonethewalls.identity.policy;

/** Stable admission decision based only on the authenticated public player ID. */
public enum PlayerBanAdmissionDecision {
    ALLOWED,
    PLAYER_BANNED;

    public boolean isAllowed() {
        return this == ALLOWED;
    }
}
