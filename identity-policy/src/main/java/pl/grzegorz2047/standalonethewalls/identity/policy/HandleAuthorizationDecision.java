package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Optional;

/** Stable, bounded result of authorizing one authenticated player identity for one handle. */
public enum HandleAuthorizationDecision {
    GLOBAL_ACCEPTED(true, HandleVerificationLevel.GLOBAL_VERIFIED),
    LOCAL_FIRST_USE_ACCEPTED(true, HandleVerificationLevel.LOCAL_UNVERIFIED),
    LOCAL_RETURNING_ACCEPTED(true, HandleVerificationLevel.LOCAL_UNVERIFIED),
    REGISTRY_UNAVAILABLE(false, null),
    UNKNOWN_GLOBAL_HANDLE(false, null),
    REVOKED_GLOBAL_HANDLE(false, null),
    GLOBAL_PLAYER_MISMATCH(false, null),
    LOCAL_BINDING_CONFLICT(false, null);

    private final boolean accepted;
    private final HandleVerificationLevel verificationLevel;

    HandleAuthorizationDecision(boolean accepted, HandleVerificationLevel verificationLevel) {
        this.accepted = accepted;
        this.verificationLevel = verificationLevel;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public Optional<HandleVerificationLevel> verificationLevel() {
        return Optional.ofNullable(verificationLevel);
    }
}
