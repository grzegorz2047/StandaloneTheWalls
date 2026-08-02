package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/** Typed policy result before a cryptographically authenticated session may enter a lobby queue. */
public sealed interface AuthenticatedPlayerAdmissionResult
        permits AuthenticatedPlayerAdmissionResult.Accepted,
                AuthenticatedPlayerAdmissionResult.Rejected {
    PlayerSessionAdmissionStatus status();

    record Accepted(PlayerSessionAdmissionStatus status, AuthorizedPlayerSession session)
            implements AuthenticatedPlayerAdmissionResult {
        public Accepted {
            status = Objects.requireNonNull(status, "status");
            session = Objects.requireNonNull(session, "session");
            if (!status.isAccepted()) {
                throw new IllegalArgumentException(
                        "accepted admission requires an accepted status");
            }
        }
    }

    record Rejected(PlayerSessionAdmissionStatus status)
            implements AuthenticatedPlayerAdmissionResult {
        public Rejected {
            status = Objects.requireNonNull(status, "status");
            if (status.isAccepted()) {
                throw new IllegalArgumentException("rejected admission requires a rejected status");
            }
        }
    }
}
