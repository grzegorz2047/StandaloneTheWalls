package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;

/** Bounded public result. Internal exceptions and sensitive values never cross the wire. */
public record IdentityResultPayload(IdentityResultStatus status) {
    public IdentityResultPayload {
        Objects.requireNonNull(status, "status");
    }

    public String publicCode() {
        return status.publicCode();
    }

    public boolean isAccepted() {
        return status.isAccepted();
    }
}
