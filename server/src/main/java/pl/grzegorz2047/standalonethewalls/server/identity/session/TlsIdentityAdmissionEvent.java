package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/** Bounded diagnostic event without addresses, identities, payloads, or exception text. */
public record TlsIdentityAdmissionEvent(
        Code code, Optional<PlayerSessionAdmissionStatus> admissionStatus) {
    public TlsIdentityAdmissionEvent {
        code = Objects.requireNonNull(code, "code");
        admissionStatus = Objects.requireNonNull(admissionStatus, "admissionStatus");
        if ((code == Code.ADMISSION_RESULT) != admissionStatus.isPresent()) {
            throw new IllegalArgumentException(
                    "only admission-result events may contain an admission status");
        }
    }

    public static TlsIdentityAdmissionEvent admission(PlayerSessionAdmissionStatus status) {
        return new TlsIdentityAdmissionEvent(
                Code.ADMISSION_RESULT, Optional.of(Objects.requireNonNull(status, "status")));
    }

    public static TlsIdentityAdmissionEvent failure(Code code) {
        if (code == Code.ADMISSION_RESULT) {
            throw new IllegalArgumentException("admission-result requires a status");
        }
        return new TlsIdentityAdmissionEvent(code, Optional.empty());
    }

    public enum Code {
        ADMISSION_RESULT,
        GATEWAY_CLOSED,
        BOOTSTRAP_FAILED,
        IDENTITY_EXCHANGE_FAILED,
        ADMISSION_RESULT_SEND_FAILED,
        INTERNAL_FAILURE
    }
}
