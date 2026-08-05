package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.server.identity.SessionIdentityAdmissionDecision;

class AuthenticatedPlayerAdmissionCoordinatorTest {
    @Test
    void fullQueueRejectsBeforeCallingIdentityPolicy() {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession occupyingTransport = new TestSession(1);
        try (AuthorizedPlayerSessionQueue.Reservation reservation =
                queue.tryReserve().orElseThrow()) {
            assertThat(
                            reservation.commit(
                                    new AuthorizedPlayerSession(
                                            occupyingTransport,
                                            HandleVerificationLevel.LOCAL_UNVERIFIED)))
                    .isTrue();
        }
        AtomicInteger policyCalls = new AtomicInteger();
        AuthenticatedPlayerAdmissionCoordinator coordinator =
                coordinator(
                        queue,
                        (handle, playerId) -> {
                            policyCalls.incrementAndGet();
                            return SessionIdentityAdmissionDecision.LOCAL_FIRST_USE_ACCEPTED;
                        });

        try (AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission prepared =
                coordinator.prepare(new TestSession(2))) {
            assertThat(prepared)
                    .isInstanceOf(
                            AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission.Rejected
                                    .class);
            assertThat(prepared.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.SERVER_CAPACITY_EXCEEDED);
        }

        assertThat(policyCalls).hasValue(0);
        assertThat(queue.size()).isEqualTo(1);
        queue.close();
        assertThat(occupyingTransport.closeCount()).isEqualTo(1);
    }

    @Test
    void policyRejectionCancelsReservationWithoutQueueingSession() {
        AuthorizedPlayerSessionQueue queue = queue(1);
        AuthenticatedPlayerAdmissionCoordinator coordinator =
                coordinator(
                        queue,
                        (handle, playerId) -> SessionIdentityAdmissionDecision.PLAYER_BANNED);
        TestSession rejectedTransport = new TestSession(1);

        try (AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission prepared =
                coordinator.prepare(rejectedTransport)) {
            assertThat(prepared.status()).isEqualTo(PlayerSessionAdmissionStatus.PLAYER_BANNED);
        }

        assertThat(queue.size()).isZero();
        assertThat(queue.reservedSlotCount()).isZero();
        try (AuthorizedPlayerSessionQueue.Reservation available =
                queue.tryReserve().orElseThrow()) {
            assertThat(available).isNotNull();
        }
        queue.close();
        assertThat(rejectedTransport.closeCount()).isZero();
    }

    @Test
    void acceptedPolicyRetainsReservationUntilExplicitCommit() {
        AuthorizedPlayerSessionQueue queue = queue(1);
        AuthenticatedPlayerAdmissionCoordinator coordinator =
                coordinator(
                        queue,
                        (handle, playerId) ->
                                SessionIdentityAdmissionDecision.LOCAL_RETURNING_ACCEPTED);
        TestSession acceptedTransport = new TestSession(1);

        try (AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission prepared =
                coordinator.prepare(acceptedTransport)) {
            assertThat(prepared)
                    .isInstanceOf(
                            AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission.Accepted
                                    .class);
            assertThat(queue.size()).isZero();
            assertThat(queue.reservedSlotCount()).isEqualTo(1);
            AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission.Accepted accepted =
                    (AuthenticatedPlayerAdmissionCoordinator.PreparedAdmission.Accepted) prepared;
            assertThat(accepted.commit()).isTrue();
        }

        assertThat(queue.reservedSlotCount()).isZero();
        assertThat(queue.size()).isEqualTo(1);
        queue.close();
        assertThat(acceptedTransport.closeCount()).isEqualTo(1);
    }

    @Test
    void policyFailureReleasesReservation() {
        AuthorizedPlayerSessionQueue queue = queue(1);
        AuthenticatedPlayerAdmissionCoordinator coordinator =
                coordinator(
                        queue,
                        (handle, playerId) -> {
                            throw new IllegalStateException("test policy failure");
                        });

        assertThatThrownBy(() -> coordinator.prepare(new TestSession(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test policy failure");
        assertThat(queue.reservedSlotCount()).isZero();
        try (AuthorizedPlayerSessionQueue.Reservation available =
                queue.tryReserve().orElseThrow()) {
            assertThat(available).isNotNull();
        }
        queue.close();
    }

    private static AuthenticatedPlayerAdmissionCoordinator coordinator(
            AuthorizedPlayerSessionQueue queue,
            AuthenticatedPlayerAdmissionService.SessionIdentityAuthorizer authorizer) {
        return new AuthenticatedPlayerAdmissionCoordinator(
                new AuthenticatedPlayerAdmissionService(authorizer), queue);
    }

    private static AuthorizedPlayerSessionQueue queue(int capacity) {
        return new AuthorizedPlayerSessionQueue(capacity, Duration.ofSeconds(1));
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
        public SecureChannelBinding channelBinding() {
            return new SecureChannelBinding(new byte[SecureChannelBinding.BYTES]);
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
