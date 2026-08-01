package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

class ChallengeLedgerTest {
    @Test
    void consumesAChallengeBeforeVerificationAndRejectsReplay() throws IdentityException {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T17:00:00Z"));
        IdentityChallengeService service = service(clock, 4);
        UUID session = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge challenge = service.issue("server.eu-1", session);
        IdentityProof proof =
                IdentityProof.create(
                        identity,
                        ProtocolVersion.CURRENT,
                        challenge,
                        new CanonicalHandle("player_one"));

        assertTrue(service.verify(session, proof).isAccepted());
        assertEquals(
                IdentityVerification.Status.MISSING_CHALLENGE,
                service.verify(session, proof).status());
        assertEquals(0, service.outstandingCount());
    }

    @Test
    void failedSignatureStillConsumesTheChallenge() throws IdentityException {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T17:00:00Z"));
        IdentityChallengeService service = service(clock, 4);
        UUID session = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge challenge = service.issue("server.eu-1", session);
        IdentityProof proof =
                IdentityProof.create(
                        identity,
                        ProtocolVersion.CURRENT,
                        challenge,
                        new CanonicalHandle("player_one"));
        byte[] signature = proof.signature();
        signature[0] ^= 1;
        IdentityProof badProof =
                new IdentityProof(
                        proof.protocolVersion(),
                        proof.handle(),
                        proof.playerId(),
                        proof.publicKey(),
                        signature);

        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                service.verify(session, badProof).status());
        assertEquals(
                IdentityVerification.Status.MISSING_CHALLENGE,
                service.verify(session, proof).status());
    }

    @Test
    void expiresChallengesAtTheExactDeadline() throws IdentityException {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T17:00:00Z"));
        IdentityChallengeService service = service(clock, 4);
        UUID session = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge challenge = service.issue("server.eu-1", session);
        IdentityProof proof =
                IdentityProof.create(
                        identity,
                        ProtocolVersion.CURRENT,
                        challenge,
                        new CanonicalHandle("player_one"));

        clock.advance(Duration.ofSeconds(30));

        assertEquals(
                IdentityVerification.Status.EXPIRED_CHALLENGE,
                service.verify(session, proof).status());
    }

    @Test
    void reissuingForTheSameSessionInvalidatesTheOldNonceWithoutUsingMoreCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T17:00:00Z"));
        IdentityChallengeService service = service(clock, 1);
        UUID session = UUID.fromString("11111111-2222-3333-4444-555555555555");

        IdentityChallenge first = service.issue("server.eu-1", session);
        IdentityChallenge replacement = service.issue("server.eu-1", session);

        assertNotEquals(first.nonce()[0], replacement.nonce()[0]);
        assertEquals(1, service.outstandingCount());
        assertThrows(
                IllegalStateException.class,
                () ->
                        service.issue(
                                "server.eu-1",
                                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));
    }

    @Test
    void rejectsUnsafeLedgerConfiguration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T17:00:00Z"));
        DeterministicRandom random = new DeterministicRandom();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChallengeLedger(clock, random, Duration.ZERO, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChallengeLedger(clock, random, Duration.ofMinutes(6), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChallengeLedger(clock, random, Duration.ofSeconds(1), 0));
    }

    private static IdentityChallengeService service(MutableClock clock, int maximumOutstanding) {
        return new IdentityChallengeService(
                new ChallengeLedger(
                        clock,
                        new DeterministicRandom(),
                        Duration.ofSeconds(30),
                        maximumOutstanding));
    }

    private static final class DeterministicRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private int next;

        @Override
        public void nextBytes(byte[] bytes) {
            next++;
            java.util.Arrays.fill(bytes, (byte) next);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
