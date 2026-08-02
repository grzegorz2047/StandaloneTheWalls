package pl.grzegorz2047.standalonethewalls.identity.policy;

/** Stable result of one atomic local player-ban administration operation. */
public enum LocalPlayerBanAdministrationResult {
    BANNED,
    ALREADY_BANNED,
    UNBANNED,
    NOT_BANNED,
    CAPACITY_EXCEEDED
}
