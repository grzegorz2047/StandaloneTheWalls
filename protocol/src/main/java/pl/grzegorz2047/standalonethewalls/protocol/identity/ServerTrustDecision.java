package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.Optional;

/** Pure trust inspection result. Inspection never changes the trust store. */
public record ServerTrustDecision(Status status, Optional<ServerTrustRecord> existingRecord) {
    public ServerTrustDecision {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(existingRecord, "existingRecord");
    }

    public boolean isTrusted() {
        return status == Status.TRUSTED;
    }

    public enum Status {
        TRUSTED,
        FIRST_USE_REQUIRES_CONFIRMATION,
        CHANGED_IDENTITY,
        EXPECTED_PIN_MISMATCH
    }
}
