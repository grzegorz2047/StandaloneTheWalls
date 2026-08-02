package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.server.lobby.MinimalLobbyRuntime;

class MinimalLobbyRuntimeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "z".repeat(52));

    @Test
    void transfersOneAuthorizedSessionAndReleasesCapacityAfterEof()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, transport);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> transport.channel.sent().size() >= 2);

            SentMessage joinedMessage = transport.channel.sent().get(0);
            SentMessage snapshotMessage = transport.channel.sent().get(1);
            assertThat(joinedMessage.messageType()).isEqualTo(MessageType.LOBBY_JOINED);
            assertThat(snapshotMessage.messageType()).isEqualTo(MessageType.LOBBY_SNAPSHOT);
            LobbyJoined joined = LobbyProtocolCodec.decodeJoined(joinedMessage.payload());
            LobbySnapshot snapshot = LobbyProtocolCodec.decodeSnapshot(snapshotMessage.payload());
            assertThat(joined.revision()).isEqualTo(1L);
            assertThat(joined.self().playerId()).isEqualTo(transport.playerId());
            assertThat(snapshot.revision()).isEqualTo(1L);
            assertThat(snapshot.members()).containsExactly(joined.self());
            assertThat(lobby.memberCount()).isEqualTo(1);
            assertThat(queue.activeTransferCount()).isEqualTo(1);

            transport.channel.completeEof();
            waitUntil(() -> lobby.memberCount() == 0 && queue.activeTransferCount() == 0);

            assertThat(transport.closeCount()).isEqualTo(1);
            assertThat(lobby.revision()).isEqualTo(2L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void broadcastsStrictlySortedCompleteSnapshotsToTwoMembers()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession bravo = new TestSession(1, playerId('b'), "bravo");
        TestSession alpha = new TestSession(2, playerId('a'), "alpha");
        enqueue(queue, bravo);
        enqueue(queue, alpha);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(
                    () ->
                            latestSnapshotMessage(bravo).isPresent()
                                    && latestSnapshotMessage(alpha).isPresent()
                                    && lobby.memberCount() == 2);

            LobbySnapshot bravoSnapshot =
                    LobbyProtocolCodec.decodeSnapshot(
                            latestSnapshotMessage(bravo).orElseThrow().payload());
            LobbySnapshot alphaSnapshot =
                    LobbyProtocolCodec.decodeSnapshot(
                            latestSnapshotMessage(alpha).orElseThrow().payload());

            assertThat(bravoSnapshot).isEqualTo(alphaSnapshot);
            assertThat(bravoSnapshot.revision()).isEqualTo(2L);
            assertThat(bravoSnapshot.members())
                    .extracting(member -> member.playerId())
                    .containsExactly(alpha.playerId(), bravo.playerId());
            assertThat(queue.activeTransferCount()).isEqualTo(2);
        } finally {
            lobby.close();
            queue.close();
        }
        assertThat(bravo.closeCount()).isEqualTo(1);
        assertThat(alpha.closeCount()).isEqualTo(1);
        assertThat(queue.activeTransferCount()).isZero();
    }

    @Test
    void rejectsASecondActiveSessionForTheSamePlayerId() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        PlayerId sharedPlayerId = playerId('a');
        TestSession first = new TestSession(1, sharedPlayerId, "alpha");
        TestSession duplicate = new TestSession(2, sharedPlayerId, "alpha");
        enqueue(queue, first);
        enqueue(queue, duplicate);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1 && duplicate.closeCount() == 1);

            assertThat(first.closeCount()).isZero();
            assertThat(queue.activeTransferCount()).isEqualTo(1);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void anyClientMessageBeforeAProtocolIsIntroducedFailsClosed() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, transport);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);
            transport.channel.completeMessage(
                    new ProtocolEnvelope(
                            ProtocolVersion.CURRENT,
                            MessageType.PING,
                            transport.sessionId(),
                            0L,
                            new byte[0]));

            waitUntil(() -> lobby.memberCount() == 0 && queue.activeTransferCount() == 0);

            assertThat(transport.closeCount()).isEqualTo(1);
            assertThat(lobby.revision()).isEqualTo(2L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void runtimeShutdownClosesOwnedSessionsAndReturnsEverySlot() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession first = new TestSession(1, playerId('a'), "alpha");
        TestSession second = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, first);
        enqueue(queue, second);
        MinimalLobbyRuntime lobby = lobby(queue);
        lobby.start();
        waitUntil(() -> lobby.memberCount() == 2);

        lobby.close();
        lobby.close();

        assertThat(lobby.isRunning()).isFalse();
        assertThat(lobby.memberCount()).isZero();
        assertThat(first.closeCount()).isEqualTo(1);
        assertThat(second.closeCount()).isEqualTo(1);
        assertThat(queue.activeTransferCount()).isZero();
        queue.close();
    }

    private static MinimalLobbyRuntime lobby(AuthorizedPlayerSessionQueue queue) {
        return new MinimalLobbyRuntime(
                queue, Duration.ofSeconds(1), Duration.ofSeconds(2), ignored -> {});
    }

    private static AuthorizedPlayerSessionQueue queue(int capacity) {
        return new AuthorizedPlayerSessionQueue(capacity, Duration.ofSeconds(1));
    }

    private static void enqueue(AuthorizedPlayerSessionQueue queue, TestSession transport) {
        AuthorizedPlayerSessionQueue.Reservation reservation = queue.tryReserve().orElseThrow();
        assertThat(
                        reservation.commit(
                                new AuthorizedPlayerSession(
                                        transport, HandleVerificationLevel.LOCAL_UNVERIFIED)))
                .isTrue();
    }

    private static Optional<SentMessage> latestSnapshotMessage(TestSession session) {
        List<SentMessage> snapshots =
                session.channel.sent().stream()
                        .filter(message -> message.messageType() == MessageType.LOBBY_SNAPSHOT)
                        .toList();
        return snapshots.isEmpty()
                ? Optional.empty()
                : Optional.of(snapshots.get(snapshots.size() - 1));
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
    }

    private static PlayerId playerId(char first) {
        return new PlayerId("sf1_" + first + "a".repeat(51));
    }

    private record SentMessage(MessageType messageType, byte[] payload) {
        private SentMessage {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private static final class TestSession implements AuthenticatedPlayerSession {
        private final UUID sessionId;
        private final PlayerId playerId;
        private final CanonicalHandle handle;
        private final StubReliableChannel channel = new StubReliableChannel();
        private final AtomicInteger closes = new AtomicInteger();

        private TestSession(int suffix, PlayerId playerId, String handle) {
            sessionId = new UUID(0x4000L + suffix, 0x8000L + suffix);
            this.playerId = playerId;
            this.handle = new CanonicalHandle(handle);
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
            return playerId;
        }

        @Override
        public CanonicalHandle handle() {
            return handle;
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
        private final CopyOnWriteArrayList<SentMessage> sent = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Optional<ProtocolEnvelope>> receive =
                new CompletableFuture<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger sequences = new AtomicInteger();

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            if (!open.get()) {
                CompletableFuture<ReliableSendResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("channel is closed"));
                return failed;
            }
            sent.add(new SentMessage(messageType, payload));
            return CompletableFuture.completedFuture(
                    new ReliableSendResult(sequences.getAndIncrement()));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            return receive.minimalCompletionStage();
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            open.set(false);
            receive.complete(Optional.empty());
            return CompletableFuture.completedFuture(null);
        }

        private List<SentMessage> sent() {
            return List.copyOf(sent);
        }

        private void completeEof() {
            receive.complete(Optional.empty());
        }

        private void completeMessage(ProtocolEnvelope message) {
            receive.complete(Optional.of(message));
        }
    }
}
