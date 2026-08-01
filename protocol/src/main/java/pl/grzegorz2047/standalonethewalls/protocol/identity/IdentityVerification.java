package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.Optional;

/** Semantic result safe to map to a bounded protocol response. */
public record IdentityVerification(
        Status status, Optional<PlayerId> playerId, Optional<CanonicalHandle> handle) {

    public IdentityVerification {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(handle, "handle");
        boolean accepted = status == Status.ACCEPTED;
        if (accepted != (playerId.isPresent() && handle.isPresent())) {
            throw new IllegalArgumentException("only accepted verification exposes identity data");
        }
    }

    public static IdentityVerification accepted(PlayerId playerId, CanonicalHandle handle) {
        return new IdentityVerification(
                Status.ACCEPTED, Optional.of(playerId), Optional.of(handle));
    }

    public static IdentityVerification rejected(Status status) {
        if (status == Status.ACCEPTED) {
            throw new IllegalArgumentException("accepted status requires identity data");
        }
        return new IdentityVerification(status, Optional.empty(), Optional.empty());
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    public enum Status {
        ACCEPTED,
        UNSUPPORTED_VERSION,
        INVALID_PUBLIC_KEY,
        PLAYER_ID_MISMATCH,
        INVALID_SIGNATURE,
        MISSING_CHALLENGE,
        EXPIRED_CHALLENGE,
        CRYPTOGRAPHY_FAILURE
    }
}
