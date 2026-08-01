package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded, thread-safe ledger that permits exactly one verification attempt per challenge. */
public final class ChallengeLedger {
    private final Clock clock;
    private final SecureRandom random;
    private final Duration lifetime;
    private final int maximumOutstanding;
    private final Map<UUID, IdentityChallenge> challenges = new HashMap<>();

    public ChallengeLedger(
            Clock clock, SecureRandom random, Duration lifetime, int maximumOutstanding) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero()
                || lifetime.isNegative()
                || lifetime.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException(
                    "challenge lifetime must be between 1 ns and 5 minutes");
        }
        if (maximumOutstanding < 1 || maximumOutstanding > 100_000) {
            throw new IllegalArgumentException("maximumOutstanding is outside the safe range");
        }
        this.maximumOutstanding = maximumOutstanding;
    }

    public synchronized IdentityChallenge issue(String serverId, UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Instant now = clock.instant();
        removeExpired(now);
        if (!challenges.containsKey(sessionId) && challenges.size() >= maximumOutstanding) {
            throw new IllegalStateException("outstanding identity challenge limit reached");
        }
        byte[] nonce = new byte[IdentityChallenge.NONCE_BYTES];
        random.nextBytes(nonce);
        IdentityChallenge challenge =
                new IdentityChallenge(serverId, sessionId, nonce, now.plus(lifetime));
        challenges.put(sessionId, challenge);
        return challenge;
    }

    public synchronized ChallengeConsumption consume(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        IdentityChallenge challenge = challenges.remove(sessionId);
        if (challenge == null) {
            return ChallengeConsumption.missing();
        }
        if (challenge.isExpired(clock.instant())) {
            return ChallengeConsumption.expired();
        }
        return ChallengeConsumption.available(challenge);
    }

    public synchronized int outstandingCount() {
        removeExpired(clock.instant());
        return challenges.size();
    }

    private void removeExpired(Instant now) {
        challenges.values().removeIf(challenge -> challenge.isExpired(now));
    }
}
