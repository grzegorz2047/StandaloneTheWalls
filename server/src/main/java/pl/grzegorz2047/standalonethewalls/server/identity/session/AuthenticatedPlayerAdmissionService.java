package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.server.identity.SessionIdentityAdmissionDecision;

/** Applies the one process-owned local identity runtime to a verified transport session. */
public final class AuthenticatedPlayerAdmissionService {
    private final SessionIdentityAuthorizer authorizer;

    public AuthenticatedPlayerAdmissionService(LocalIdentityRuntime identityRuntime) {
        this(Objects.requireNonNull(identityRuntime, "identityRuntime")::admit);
    }

    AuthenticatedPlayerAdmissionService(SessionIdentityAuthorizer authorizer) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public AuthenticatedPlayerAdmissionResult evaluate(AuthenticatedPlayerSession session) {
        AuthenticatedPlayerSession authenticated = Objects.requireNonNull(session, "session");
        SessionIdentityAdmissionDecision decision =
                authorizer.admit(authenticated.handle(), authenticated.playerId());
        PlayerSessionAdmissionStatus status = status(decision);
        if (!decision.isAccepted()) {
            return new AuthenticatedPlayerAdmissionResult.Rejected(status);
        }
        return new AuthenticatedPlayerAdmissionResult.Accepted(
                status,
                new AuthorizedPlayerSession(
                        authenticated, decision.verificationLevel().orElseThrow()));
    }

    private static PlayerSessionAdmissionStatus status(SessionIdentityAdmissionDecision decision) {
        return switch (Objects.requireNonNull(decision, "decision")) {
            case GLOBAL_ACCEPTED -> PlayerSessionAdmissionStatus.GLOBAL_ACCEPTED;
            case LOCAL_FIRST_USE_ACCEPTED -> PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED;
            case LOCAL_RETURNING_ACCEPTED -> PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED;
            case PLAYER_BANNED -> PlayerSessionAdmissionStatus.PLAYER_BANNED;
            case REGISTRY_UNAVAILABLE -> PlayerSessionAdmissionStatus.REGISTRY_UNAVAILABLE;
            case REGISTRY_STALE -> PlayerSessionAdmissionStatus.REGISTRY_STALE;
            case UNKNOWN_GLOBAL_HANDLE -> PlayerSessionAdmissionStatus.UNKNOWN_GLOBAL_HANDLE;
            case REVOKED_GLOBAL_HANDLE -> PlayerSessionAdmissionStatus.REVOKED_GLOBAL_HANDLE;
            case GLOBAL_PLAYER_MISMATCH -> PlayerSessionAdmissionStatus.GLOBAL_PLAYER_MISMATCH;
            case LOCAL_BINDING_CONFLICT -> PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT;
            case LOCAL_BINDING_CAPACITY_EXCEEDED ->
                    PlayerSessionAdmissionStatus.LOCAL_BINDING_CAPACITY_EXCEEDED;
        };
    }

    @FunctionalInterface
    interface SessionIdentityAuthorizer {
        SessionIdentityAdmissionDecision admit(CanonicalHandle handle, PlayerId playerId);
    }
}
