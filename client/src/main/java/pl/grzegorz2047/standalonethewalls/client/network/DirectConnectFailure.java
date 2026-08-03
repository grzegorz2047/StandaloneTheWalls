package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

/** Public failure payload without exception text, addresses, certificates, keys, or proofs. */
public record DirectConnectFailure(
        DirectConnectFailureCode code, Optional<PlayerSessionAdmissionStatus> admissionStatus) {
    public DirectConnectFailure {
        Objects.requireNonNull(code, "code");
        admissionStatus = Objects.requireNonNull(admissionStatus, "admissionStatus");
        if (code != DirectConnectFailureCode.ADMISSION_REJECTED && admissionStatus.isPresent()) {
            throw new IllegalArgumentException(
                    "admissionStatus is allowed only for ADMISSION_REJECTED");
        }
        if (code == DirectConnectFailureCode.ADMISSION_REJECTED && admissionStatus.isEmpty()) {
            throw new IllegalArgumentException(
                    "ADMISSION_REJECTED requires the public admission status");
        }
    }

    public static DirectConnectFailure of(DirectConnectFailureCode code) {
        return new DirectConnectFailure(code, Optional.empty());
    }

    public static DirectConnectFailure admissionRejected(PlayerSessionAdmissionStatus status) {
        return new DirectConnectFailure(
                DirectConnectFailureCode.ADMISSION_REJECTED,
                Optional.of(Objects.requireNonNull(status, "status")));
    }
}
