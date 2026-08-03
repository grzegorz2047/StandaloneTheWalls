package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSessionTestFactory;

class ConnectedLobbySessionTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final CanonicalHandle HANDLE = new CanonicalHandle("alpha");
    private static final UUID SESSION_ID = UUID.fromString("12345678-1234-4234-8234-1234567890ab");

    @Test
    void acceptsOnlyNewerSnapshotsContainingExactSelfAndClosesOnce()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        AuthenticatedReliableSession transport =
                AuthenticatedReliableSessionTestFactory.create(channel, PLAYER_ID, HANDLE);
        AtomicInteger releases = new AtomicInteger();
        ConnectedLobbySession session =
                new ConnectedLobbySession(
                        transport,
                        snapshot(1L, HANDLE),
                        matchSnapshot(1L, 1L, 1),
                        ignored -> releases.incrementAndGet());
        session.startReceiving();

        channel.deliver(snapshotEnvelope(snapshot(2L, HANDLE), 1L));
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        assertTrue(session.isOpen());
        assertEquals(0, releases.get());

        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(1, channel.closeCount());
        assertEquals(1, releases.get());
        assertTrue(session.terminalFailure().isEmpty());
    }

    @Test
    void preservesAuthoritativeTeamAndReadyStateFromSchemaTwo()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(1L, HANDLE));
        session.startReceiving();
        LobbySnapshot roster =
                new LobbySnapshot(
                        2L, List.of(new LobbyMember(PLAYER_ID, HANDLE, LobbyTeam.GREEN, true)));

        channel.deliver(snapshotEnvelope(roster, 1L));
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        LobbyMember self = session.currentSnapshot().members().getFirst();
        assertEquals(LobbyTeam.GREEN, self.team());
        assertTrue(self.ready());
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void acceptsLegacySchemaOneAsUnassignedAndNotReady()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(1L, HANDLE));
        session.startReceiving();

        channel.deliver(legacySnapshotEnvelope(2L, HANDLE, 1L));
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        LobbyMember self = session.currentSnapshot().members().getFirst();
        assertEquals(LobbyTeam.UNASSIGNED, self.team());
        assertFalse(self.ready());
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void staleSnapshotClosesFailClosed()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(2L, HANDLE));
        session.startReceiving();

        channel.deliver(snapshotEnvelope(snapshot(2L, HANDLE), 1L));
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(DirectConnectFailureCode.LOBBY_SNAPSHOT_STALE, failure.orElseThrow().code());
        assertFalse(session.isOpen());
        assertEquals(1, channel.closeCount());
    }

    @Test
    void changedSelfHandleClosesFailClosed()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(1L, HANDLE));
        session.startReceiving();

        channel.deliver(snapshotEnvelope(snapshot(2L, new CanonicalHandle("different")), 1L));
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(
                DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH, failure.orElseThrow().code());
        assertEquals(1, channel.closeCount());
    }

    @Test
    void unexpectedMessageClosesFailClosed()
            throws InterruptedException, ExecutionException, TimeoutException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(1L, HANDLE));
        session.startReceiving();

        channel.deliver(
                new ProtocolEnvelope(
                        ProtocolVersion.CURRENT, MessageType.PING, SESSION_ID, 1L, new byte[0]));
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(DirectConnectFailureCode.UNEXPECTED_MESSAGE, failure.orElseThrow().code());
        assertEquals(1, channel.closeCount());
    }

    private static ConnectedLobbySession createSession(
            StubReliableChannel channel, LobbySnapshot initial) {
        AuthenticatedReliableSession transport =
                AuthenticatedReliableSessionTestFactory.create(channel, PLAYER_ID, HANDLE);
        return new ConnectedLobbySession(
                transport,
                initial,
                matchSnapshot(1L, initial.revision(), initial.members().size()),
                ignored -> {});
    }

    private static LobbyMatchPhaseSnapshot matchSnapshot(
            long revision, long rosterRevision, int connectedPlayers) {
        return new LobbyMatchPhaseSnapshot(
                revision,
                rosterRevision,
                LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK,
                LobbyMatchPhase.WAITING_FOR_PLAYERS,
                0L,
                connectedPlayers,
                1L,
                LobbyCountdownCancellationReason.NONE);
    }

    private static LobbySnapshot snapshot(long revision, CanonicalHandle handle) {
        return new LobbySnapshot(revision, List.of(new LobbyMember(PLAYER_ID, handle)));
    }

    private static ProtocolEnvelope snapshotEnvelope(LobbySnapshot snapshot, long sequence) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT,
                MessageType.LOBBY_SNAPSHOT,
                SESSION_ID,
                sequence,
                LobbyProtocolCodec.encodeSnapshot(snapshot));
    }

    private static ProtocolEnvelope legacySnapshotEnvelope(
            long revision, CanonicalHandle handle, long sequence) {
        byte[] playerId = PLAYER_ID.value().getBytes(StandardCharsets.US_ASCII);
        byte[] handleBytes = handle.value().getBytes(StandardCharsets.US_ASCII);
        byte[] payload =
                ByteBuffer.allocate(1 + Long.BYTES + 1 + playerId.length + 1 + handleBytes.length)
                        .put((byte) 1)
                        .putLong(revision)
                        .put((byte) 1)
                        .put(playerId)
                        .put((byte) handleBytes.length)
                        .put(handleBytes)
                        .array();
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT, MessageType.LOBBY_SNAPSHOT, SESSION_ID, sequence, payload);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final ArrayDeque<Optional<ProtocolEnvelope>> pending = new ArrayDeque<>();
        private final ArrayDeque<CompletableFuture<Optional<ProtocolEnvelope>>> waiters =
                new ArrayDeque<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            return CompletableFuture.completedFuture(new ReliableSendResult(0L));
        }

        @Override
        public synchronized CompletionStage<Optional<ProtocolEnvelope>> receive() {
            Optional<ProtocolEnvelope> queued = pending.pollFirst();
            if (queued != null) {
                return CompletableFuture.completedFuture(queued);
            }
            CompletableFuture<Optional<ProtocolEnvelope>> waiter = new CompletableFuture<>();
            waiters.addLast(waiter);
            return waiter.minimalCompletionStage();
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            if (open.compareAndSet(true, false)) {
                closes.incrementAndGet();
                deliver(Optional.empty());
            }
            return CompletableFuture.completedFuture(null);
        }

        private void deliver(ProtocolEnvelope envelope) {
            deliver(Optional.of(envelope));
        }

        private synchronized void deliver(Optional<ProtocolEnvelope> envelope) {
            CompletableFuture<Optional<ProtocolEnvelope>> waiter = waiters.pollFirst();
            if (waiter == null) {
                pending.addLast(envelope);
            } else {
                waiter.complete(envelope);
            }
        }

        private int closeCount() {
            return closes.get();
        }
    }
}
