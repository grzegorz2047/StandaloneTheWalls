package pl.grzegorz2047.standalonethewalls.identity.policy;

/** Stable result of one atomic local handle administration operation. */
public enum LocalHandleAdministrationResult {
    RESERVED,
    ALREADY_MATCHED,
    CONFLICT,
    UNBOUND,
    REBOUND,
    NOT_FOUND,
    EXPECTATION_MISMATCH,
    SAME_PLAYER,
    CAPACITY_EXCEEDED
}
