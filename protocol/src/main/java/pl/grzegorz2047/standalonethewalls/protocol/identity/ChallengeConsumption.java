package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.Optional;

/** Atomic one-time challenge removal result. */
public record ChallengeConsumption(Status status, Optional<IdentityChallenge> challenge) {
    public ChallengeConsumption {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(challenge, "challenge");
        if ((status == Status.AVAILABLE) != challenge.isPresent()) {
            throw new IllegalArgumentException("only an available challenge exposes its value");
        }
    }

    public static ChallengeConsumption available(IdentityChallenge challenge) {
        return new ChallengeConsumption(Status.AVAILABLE, Optional.of(challenge));
    }

    public static ChallengeConsumption missing() {
        return new ChallengeConsumption(Status.MISSING, Optional.empty());
    }

    public static ChallengeConsumption expired() {
        return new ChallengeConsumption(Status.EXPIRED, Optional.empty());
    }

    public enum Status {
        AVAILABLE,
        MISSING,
        EXPIRED
    }
}
