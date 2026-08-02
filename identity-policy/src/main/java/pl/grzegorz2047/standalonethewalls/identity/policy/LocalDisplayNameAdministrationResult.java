package pl.grzegorz2047.standalonethewalls.identity.policy;

/** Stable bounded result of one display-name administration attempt. */
public enum LocalDisplayNameAdministrationResult {
    APPLIED,
    UNCHANGED,
    NOT_FOUND,
    EXPECTATION_MISMATCH,
    INVALID_VALUE,
    CAPACITY_EXCEEDED
}
