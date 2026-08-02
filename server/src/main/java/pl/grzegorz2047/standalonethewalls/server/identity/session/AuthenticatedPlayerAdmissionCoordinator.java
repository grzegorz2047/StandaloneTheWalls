package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/**
 * Reserves bounded handoff capacity before invoking identity policy that may create a TOFU binding.
 */
final class AuthenticatedPlayerAdmissionCoordinator {
    private final AuthenticatedPlayerAdmissionService admissionService;
    private final AuthorizedPlayerSessionQueue authorizedSessions;

    AuthenticatedPlayerAdmissionCoordinator(
            AuthenticatedPlayerAdmissionService admissionService,
            AuthorizedPlayerSessionQueue authorizedSessions) {
        this.admissionService = Objects.requireNonNull(admissionService, "admissionService");
        this.authorizedSessions = Objects.requireNonNull(authorizedSessions, "authorizedSessions");
    }

    PreparedAdmission prepare(AuthenticatedPlayerSession session) {
        AuthenticatedPlayerSession authenticated = Objects.requireNonNull(session, "session");
        Optional<AuthorizedPlayerSessionQueue.Reservation> reservationAttempt =
                authorizedSessions.tryReserve();
        if (reservationAttempt.isEmpty()) {
            PlayerSessionAdmissionStatus status =
                    authorizedSessions.isClosed()
                            ? PlayerSessionAdmissionStatus.SERVER_SHUTTING_DOWN
                            : PlayerSessionAdmissionStatus.SERVER_CAPACITY_EXCEEDED;
            return new PreparedAdmission.Rejected(status);
        }

        AuthorizedPlayerSessionQueue.Reservation reservation =
                reservationAttempt.orElseThrow();
        try {
            AuthenticatedPlayerAdmissionResult admission =
                    admissionService.evaluate(authenticated);
            if (admission instanceof AuthenticatedPlayerAdmissionResult.Rejected rejected) {
                reservation.close();
                return new PreparedAdmission.Rejected(rejected.status());
            }
            AuthenticatedPlayerAdmissionResult.Accepted accepted =
                    (AuthenticatedPlayerAdmissionResult.Accepted) admission;
            return new PreparedAdmission.Accepted(
                    accepted.status(), accepted.session(), reservation);
        } catch (RuntimeException exception) {
            reservation.close();
            throw exception;
        }
    }

    sealed interface PreparedAdmission extends AutoCloseable
            permits PreparedAdmission.Accepted, PreparedAdmission.Rejected {
        PlayerSessionAdmissionStatus status();

        @Override
        void close();

        final class Accepted implements PreparedAdmission {
            private final PlayerSessionAdmissionStatus status;
            private final AuthorizedPlayerSession session;
            private final AuthorizedPlayerSessionQueue.Reservation reservation;

            private Accepted(
                    PlayerSessionAdmissionStatus status,
                    AuthorizedPlayerSession session,
                    AuthorizedPlayerSessionQueue.Reservation reservation) {
                this.status = Objects.requireNonNull(status, "status");
                this.session = Objects.requireNonNull(session, "session");
                this.reservation = Objects.requireNonNull(reservation, "reservation");
                if (!status.isAccepted()) {
                    throw new IllegalArgumentException(
                            "prepared accepted admission requires an accepted status");
                }
            }

            @Override
            public PlayerSessionAdmissionStatus status() {
                return status;
            }

            AuthorizedPlayerSession session() {
                return session;
            }

            boolean commit() {
                return reservation.commit(session);
            }

            @Override
            public void close() {
                reservation.close();
            }
        }

        record Rejected(PlayerSessionAdmissionStatus status) implements PreparedAdmission {
            public Rejected {
                status = Objects.requireNonNull(status, "status");
                if (status.isAccepted()) {
                    throw new IllegalArgumentException(
                            "prepared rejection requires a rejected status");
                }
            }

            @Override
            public void close() {
                // No reservation remains owned by a prepared rejection.
            }
        }
    }
}
