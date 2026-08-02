package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

class AuthorizedPlayerSessionQueueTest {
    @Test
    void reservationBoundsCapacityAndTransferRetainsItsSlotUntilSessionClose() {
        AuthorizedPlayerSessionQueue queue =
                new AuthorizedPlayerSessionQueue(1, Duration.ofSeconds(1));
        AuthorizedPlayerSessionQueue.Reservation reservation = queue.tryReserve().orElseThrow();
        TestSession transport = new TestSession(1);
        AuthorizedPlayerSession authorized = authorized(transport);

        assertThat(queue.tryReserve()).isEmpty();
        assertThat(queue.reservedSlotCount()).isEqualTo(1);
        assertThat(reservation.commit(authorized)).isTrue();
        assertThat(queue.reservedSlotCount()).isZero();
        assertThat(queue.size()).isEqualTo(1);

        AuthorizedPlayerSession transferred = queue.poll().orElseThrow();
        assertThat(transferred.sessionId()).isEqualTo(authorized.sessionId());
        assertThat(queue.size()).isZero();
        assertThat(queue.activeTransferCount()).isEqualTo(1);
        assertThat(queue.tryReserve()).isEmpty();

        queue.close();
        assertThat(transport.closeCount()).isZero();
        assertThat(queue.isClosed()).isTrue();

        transferred.closeAsync().toCompletableFuture().join();
        transferred.closeAsync().toCompletableFuture().join();
        assertThat(transport.closeCount()).isEqualTo(1);
        assertThat(queue.activeTransferCount()).isZero();
    }

    @Test
    void closingTransferredSessionReturnsCapacityToAnOpenQueue() {
        AuthorizedPlayerSessionQueue queue =
                new AuthorizedPlayerSessionQueue(1, Duration.ofSeconds(1));
        TestSession transport = new TestSession(1);
        try (AuthorizedPlayerSessionQueue.Reservation reservation =
                queue.tryReserve().orElseThrow()) {
            assertThat(reservation.commit(authorized(transport))).isTrue();
        }
        AuthorizedPlayerSession transferred = queue.poll().orElseThrow();

        assertThat(queue.tryReserve()).isEmpty();
        transferred.closeAsync().toCompletableFuture().join();

        assertThat(queue.activeTransferCount()).isZero();
        assertThat(queue.tryReserve()).isPresent();
        queue.close();
    }

    @Test
    void closingQueueClosesEveryUntransferredSession() {
        AuthorizedPlayerSessionQueue queue =
                new AuthorizedPlayerSessionQueue(2, Duration.ofSeconds(1));
        TestSession first = new TestSession(1);
        TestSession second = new TestSession(2);
        try (AuthorizedPlayerSessionQueue.Reservation firstReservation =
                        queue.tryReserve().orElseThrow();
                AuthorizedPlayerSessionQueue.Reservation secondReservation =
                        queue.tryReserve().orElseThrow()) {
            assertThat(firstReservation.commit(authorized(first))).isTrue();
            assertThat(secondReservation.commit(authorized(second))).isTrue();
        }

        queue.close();
        queue.close();

        assertThat(first.closeCount()).isEqualTo(1);
        assertThat(second.closeCount()).isEqualTo(1);
        assertThat(queue.size()).isZero();
    }

    @Test
    void cancelledReservationReturnsCapacityAndCannotCommitTwice() {
        AuthorizedPlayerSessionQueue queue =
                new AuthorizedPlayerSessionQueue(1, Duration.ofSeconds(1));
        AuthorizedPlayerSessionQueue.Reservation cancelled = queue.tryReserve().orElseThrow();
        cancelled.close();

        AuthorizedPlayerSessionQueue.Reservation replacement = queue.tryReserve().orElseThrow();
        TestSession transport = new TestSession(1);
        AuthorizedPlayerSession authorized = authorized(transport);

        assertThat(replacement.commit(authorized)).isTrue();
        assertThat(replacement.commit(authorized)).isFalse();
        queue.close();
        assertThat(transport.closeCount()).isEqualTo(1);
    }

    @Test
    void reservationCannotCommitAfterQueueShutdown() {
        AuthorizedPlayerSessionQueue queue =
                new AuthorizedPlayerSessionQueue(1, Duration.ofSeconds(1));
        AuthorizedPlayerSessionQueue.Reservation reservation = queue.tryReserve().orElseThrow();
        TestSession transport = new TestSession(1);

        queue.close();

        assertThat(reservation.commit(authorized(transport))).isFalse();
        assertThat(queue.tryReserve()).isEmpty();
        assertThat(transport.closeCount()).isZero();
    }

    private static AuthorizedPlayerSession authorized(TestSession session) {
        return new AuthorizedPlayerSession(session, HandleVerificationLevel.LOCAL_UNVERIFIED);
    }

    private static final class TestSession implements AuthenticatedPlayerSession {
        private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
        private static final ServerId SERVER_ID = new ServerId("sfs1_" + "b".repeat(52));
        private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");

        private final UUID sessionId;
        private final AtomicInteger closes = new AtomicInteger();
        private final StubReliableChannel channel = new StubReliableChannel();

        private TestSession(int suffix) {
            sessionId = new UUID(0x4000L + suffix, 0x8000L + suffix);
        }

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public ServerId serverId() {
            return SERVER_ID;
        }

        @Override
        public PlayerId playerId() {
            return PLAYER_ID;
        }

        @Override
        public CanonicalHandle handle() {
            return HANDLE;
        }

        @Override
        public ReliableChannel reliableChannel() {
            return channel;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            closes.incrementAndGet();
            return channel.close();
        }

        private int closeCount() {
            return closes.get();
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final AtomicBoolean open = new AtomicBoolean(true);

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            return CompletableFuture.completedFuture(new ReliableSendResult(0L));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            open.set(false);
            return CompletableFuture.completedFuture(null);
        }
    }
}
