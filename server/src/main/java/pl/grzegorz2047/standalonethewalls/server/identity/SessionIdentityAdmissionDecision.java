package pl.grzegorz2047.standalonethewalls.server.identity;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationDecision;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;

/** Stable result of the server identity gate before a session may enter the lobby. */
public enum SessionIdentityAdmissionDecision {
    GLOBAL_ACCEPTED(true, HandleVerificationLevel.GLOBAL_VERIFIED),
    LOCAL_FIRST_USE_ACCEPTED(true, HandleVerificationLevel.LOCAL_UNVERIFIED),
    LOCAL_RETURNING_ACCEPTED(true, HandleVerificationLevel.LOCAL_UNVERIFIED),
    PLAYER_BANNED(false, null),
    REGISTRY_UNAVAILABLE(false, null),
    REGISTRY_STALE(false, null),
    UNKNOWN_GLOBAL_HANDLE(false, null),
    REVOKED_GLOBAL_HANDLE(false, null),
    GLOBAL_PLAYER_MISMATCH(false, null),
    LOCAL_BINDING_CONFLICT(false, null),
    LOCAL_BINDING_CAPACITY_EXCEEDED(false, null);

    private final boolean accepted;
    private final HandleVerificationLevel verificationLevel;

    SessionIdentityAdmissionDecision(
            boolean accepted, HandleVerificationLevel verificationLevel) {
        this.accepted = accepted;
        this.verificationLevel = verificationLevel;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public Optional<HandleVerificationLevel> verificationLevel() {
        return Optional.ofNullable(verificationLevel);
    }

    public static SessionIdentityAdmissionDecision fromHandle(
            HandleAuthorizationDecision decision) {
        return switch (Objects.requireNonNull(decision, "decision")) {
            case GLOBAL_ACCEPTED -> GLOBAL_ACCEPTED;
            case LOCAL_FIRST_USE_ACCEPTED -> LOCAL_FIRST_USE_ACCEPTED;
            case LOCAL_RETURNING_ACCEPTED -> LOCAL_RETURNING_ACCEPTED;
            case REGISTRY_UNAVAILABLE -> REGISTRY_UNAVAILABLE;
            case REGISTRY_STALE -> REGISTRY_STALE;
            case UNKNOWN_GLOBAL_HANDLE -> UNKNOWN_GLOBAL_HANDLE;
            case REVOKED_GLOBAL_HANDLE -> REVOKED_GLOBAL_HANDLE;
            case GLOBAL_PLAYER_MISMATCH -> GLOBAL_PLAYER_MISMATCH;
            case LOCAL_BINDING_CONFLICT -> LOCAL_BINDING_CONFLICT;
            case LOCAL_BINDING_CAPACITY_EXCEEDED -> LOCAL_BINDING_CAPACITY_EXCEEDED;
        };
    }
}
