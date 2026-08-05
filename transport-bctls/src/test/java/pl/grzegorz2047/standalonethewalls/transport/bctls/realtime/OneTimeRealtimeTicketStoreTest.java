package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

class OneTimeRealtimeTicketStoreTest {
    private static final Instant START = Instant.parse("2026-08-04T20:00:00Z");
    private static final RealtimeTicketStoreConfig CONFIG =
            new RealtimeTicketStoreConfig(4, Duration.ofMinutes(1));
    private static final RealtimeTicketContext CONTEXT =
            new RealtimeTicketContext(
                    new ServerId("sfs1_" + "a".repeat(52)),
                    UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                    new PlayerId("sf1_" + "b".repeat(52)),
                    new RealtimeChannelBindingDigest(filled(32, 7)),
                    19L);

    @Test
    void issuesAndRedeemsExactlyOnceWithDefensiveCopies() throws RealtimeTicketStoreException {
        QueueEntropy entropy = new QueueEntropy(filled(16, 1), filled(32, 2));
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(Clock.fixed(START, ZoneOffset.UTC), entropy, CONFIG);

        IssuedRealtimeTicket issued = store.issue(CONTEXT, Duration.ofSeconds(30));
        byte[] identityCopy = issued.identity().copyBytes();
        byte[] clientKeyCopy = issued.preSharedKey().copyBytes();
        identityCopy[0] = 99;
        clientKeyCopy[0] = 99;

        RealtimeTicketRedemption first = store.redeem(issued.identity());
        RealtimeTicketRedemption replay = store.redeem(issued.identity());

        assertThat(first.status()).isEqualTo(RealtimeTicketRedemption.Status.REDEEMED);
        assertThat(replay.status()).isEqualTo(RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
        try (RedeemedRealtimeTicket redeemed = first.ticket().orElseThrow()) {
            assertThat(redeemed.context()).isEqualTo(CONTEXT);
            assertThat(redeemed.expiresAt()).isEqualTo(START.plusSeconds(30));
            assertThat(redeemed.identity().copyBytes()).containsOnly(1);
            assertThat(redeemed.preSharedKey().copyBytes()).containsOnly(2);
            assertThat(redeemed.toString()).contains("redacted").doesNotContain("02020202");
        }
        assertThat(store.activeTicketCount()).isZero();
        issued.close();
        store.close();
    }

    @Test
    void expiresAtTheExactBoundaryAndThenLooksUnknown() throws RealtimeTicketStoreException {
        MutableClock clock = new MutableClock(START);
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        clock, new QueueEntropy(filled(16, 3), filled(32, 4)), CONFIG);
        IssuedRealtimeTicket issued = store.issue(CONTEXT, Duration.ofSeconds(10));

        clock.advance(Duration.ofSeconds(10));
        RealtimeTicketRedemption expired = store.redeem(issued.identity());
        RealtimeTicketRedemption second = store.redeem(issued.identity());

        assertThat(expired.status()).isEqualTo(RealtimeTicketRedemption.Status.EXPIRED);
        assertThat(second.status()).isEqualTo(RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
        assertThat(expired.ticket()).isEmpty();
        issued.close();
        store.close();
    }

    @Test
    void capacityDoesNotEvictValidTicketsButExpiredEntriesAreCleaned()
            throws RealtimeTicketStoreException {
        MutableClock clock = new MutableClock(START);
        QueueEntropy entropy =
                new QueueEntropy(filled(16, 5), filled(32, 6), filled(16, 7), filled(32, 8));
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        clock, entropy, new RealtimeTicketStoreConfig(1, Duration.ofSeconds(30)));
        IssuedRealtimeTicket first = store.issue(CONTEXT, Duration.ofSeconds(10));

        assertThatThrownBy(() -> store.issue(CONTEXT, Duration.ofSeconds(10)))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.CAPACITY_EXHAUSTED);
        assertThat(store.activeTicketCount()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(10));
        IssuedRealtimeTicket replacement = store.issue(CONTEXT, Duration.ofSeconds(10));

        assertThat(store.activeTicketCount()).isEqualTo(1);
        assertThat(store.redeem(first.identity()).status())
                .isEqualTo(RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
        assertThat(store.redeem(replacement.identity()).status())
                .isEqualTo(RealtimeTicketRedemption.Status.REDEEMED);
        first.close();
        replacement.close();
        store.close();
    }

    @Test
    void revokeRemovesAndDestroysAnUnredeemedTicket() throws RealtimeTicketStoreException {
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(START, ZoneOffset.UTC),
                        new QueueEntropy(filled(16, 21), filled(32, 22)),
                        CONFIG);
        IssuedRealtimeTicket issued = store.issue(CONTEXT, Duration.ofSeconds(30));

        assertThat(store.revoke(issued.identity())).isTrue();
        assertThat(store.revoke(issued.identity())).isFalse();
        assertThat(store.redeem(issued.identity()).status())
                .isEqualTo(RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
        assertThat(store.activeTicketCount()).isZero();

        issued.close();
        store.close();
    }

    @Test
    void concurrentRedemptionHasExactlyOneWinner()
            throws RealtimeTicketStoreException, InterruptedException, ExecutionException {
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(START, ZoneOffset.UTC),
                        new QueueEntropy(filled(16, 9), filled(32, 10)),
                        CONFIG);
        IssuedRealtimeTicket issued = store.issue(CONTEXT, Duration.ofSeconds(30));
        CountDownLatch start = new CountDownLatch(1);

        List<RealtimeTicketRedemption.Status> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RealtimeTicketRedemption> first =
                    executor.submit(
                            () -> {
                                start.await();
                                return store.redeem(issued.identity());
                            });
            Future<RealtimeTicketRedemption> second =
                    executor.submit(
                            () -> {
                                start.await();
                                return store.redeem(issued.identity());
                            });
            start.countDown();
            RealtimeTicketRedemption firstResult = first.get();
            RealtimeTicketRedemption secondResult = second.get();
            firstResult.ticket().ifPresent(RedeemedRealtimeTicket::close);
            secondResult.ticket().ifPresent(RedeemedRealtimeTicket::close);
            statuses = List.of(firstResult.status(), secondResult.status());
        }

        assertThat(statuses)
                .containsExactlyInAnyOrder(
                        RealtimeTicketRedemption.Status.REDEEMED,
                        RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);
        issued.close();
        store.close();
    }

    @Test
    void closeIsIdempotentDestroysClientKeyAndRejectsFurtherOperations()
            throws RealtimeTicketStoreException {
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(START, ZoneOffset.UTC),
                        new QueueEntropy(filled(16, 11), filled(32, 12)),
                        CONFIG);
        IssuedRealtimeTicket issued = store.issue(CONTEXT, Duration.ofSeconds(30));

        store.close();
        store.close();
        issued.close();

        assertThat(issued.preSharedKey().isDestroyed()).isTrue();
        assertThatThrownBy(() -> store.redeem(issued.identity()))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.CLOSED);
        assertThatThrownBy(() -> store.issue(CONTEXT, Duration.ofSeconds(1)))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.CLOSED);
    }

    @Test
    void rejectsInvalidLifetimeEntropyAndRepeatedIdentityCollisions()
            throws RealtimeTicketStoreException {
        OneTimeRealtimeTicketStore invalidEntropy =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(START, ZoneOffset.UTC),
                        length -> new byte[Math.max(0, length - 1)],
                        CONFIG);
        assertThatThrownBy(() -> invalidEntropy.issue(CONTEXT, Duration.ofSeconds(1)))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.INVALID_ENTROPY);

        byte[] sameIdentity = filled(16, 13);
        Queue<byte[]> collisionValues = new ArrayDeque<>();
        collisionValues.add(sameIdentity);
        collisionValues.add(filled(32, 14));
        for (int index = 0; index < 8; index++) {
            collisionValues.add(sameIdentity);
        }
        OneTimeRealtimeTicketStore collisions =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(START, ZoneOffset.UTC),
                        new QueueEntropy(collisionValues),
                        CONFIG);
        IssuedRealtimeTicket first = collisions.issue(CONTEXT, Duration.ofSeconds(30));

        assertThatThrownBy(() -> collisions.issue(CONTEXT, Duration.ofSeconds(30)))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.IDENTITY_COLLISION_LIMIT);
        assertThatThrownBy(() -> collisions.issue(CONTEXT, Duration.ZERO))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.INVALID_LIFETIME);
        assertThat(collisions.activeTicketCount()).isEqualTo(1);
        first.close();
        collisions.close();
        invalidEntropy.close();
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static final class QueueEntropy implements RealtimeTicketEntropy {
        private final Queue<byte[]> values;

        private QueueEntropy(byte[]... values) {
            this(List.of(values));
        }

        private QueueEntropy(Queue<byte[]> values) {
            this.values = new ArrayDeque<>();
            values.forEach(value -> this.values.add(value.clone()));
        }

        private QueueEntropy(List<byte[]> values) {
            this.values = new ArrayDeque<>();
            values.forEach(value -> this.values.add(value.clone()));
        }

        @Override
        public byte[] randomBytes(int length) {
            byte[] value = values.remove();
            return value.clone();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock supports UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
