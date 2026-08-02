package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
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
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;

class ConnectedLobbySessionTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final CanonicalHandle HANDLE = new CanonicalHandle("alpha");

    @Test
    void acceptsOnlyNewerSnapshotsContainingExactSelf() throws Exception {
        StubAuthenticatedSession transport = new StubAuthenticatedSession();
        AtomicInteger releases = new AtomicInteger();
        ConnectedLobbySession session =
                new ConnectedLobbySession(transport, snapshot(1L, HANDLE), ignored -> releases.incrementAndGet());

        transport.channel.deliver(snapshotEnvelope(snapshot(2L, HANDLE), 1L));
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        assertTrue(session.isOpen());
        assertEquals(2L, session.currentSnapshot().revision());
        assertEquals(0, releases.get());

        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(1, transport.closeCount.get());
        assertEquals(1, releases.get());
        assertTrue(session.terminalFailure().isEmpty());
    }

    @Test
    void staleSnapshotClosesFailClosed() throws Exception {
        StubAuthenticatedSession transport = new StubAuthenticatedSession();
        AtomicInteger releases = new AtomicInteger();
        ConnectedLobbySession session =
                new ConnectedLobbySession(transport, snapshot(2L, HANDLE), ignored -> releases.incrementAndGet());

        transport.channel.deliver(snapshotEnvelope(snapshot(2L, HANDLE), 1L));
        Optional<DirectConnectFailure> failure =
                session.termination().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(DirectConnectFailureCode.LOBBY_SNAPSHOT_STALE, failure.orElseThrow().code());
        assertFalse(session.isOpen());
        assertEquals(1, transport.closeCount.get());
        assertEquals(1, releases.get());
    }

    @Test
    void changedSelfHandleClosesFailClosed() throws Exception {
        StubAuthenticatedSession transport = new StubAuthenticatedSession();
        ConnectedLobbySession session =
                new ConnectedLobbySession(transport, snapshot(1L, HANDLE), ignored -> {});

        transport.channel.deliver(
                snapshotEnvelope(snapshot(2L, new CanonicalHandle("different")), 1L));
        Optional<DirectConnectFailure> failure =
                session.termination().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(
                DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH,
                failure.orElseThrow().code());
        assertEquals(1, transport.closeCount.get());
    }

    @Test
    void unexpectedMessageClosesFailClosed() throws Exception {
        StubAuthenticatedSession transport = new StubAuthenticatedSession();
        ConnectedLobbySession session =
                new ConnectedLobbySession(transport, snapshot(1L, HANDLE), ignored -> {});

        transport.channel.deliver(
                new ProtocolEnvelope(
                        ProtocolVersion.CURRENT,
                        MessageType.PING,
                        transport.sessionId(),
                        1L,
                        new byte[0]));
        Optional<DirectConnectFailure> failure =
                session.termination().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(DirectConnectFailureCode.UNEXPECTED_MESSAGE, failure.orElseThrow().code());
        assertEquals(1, transport.closeCount.get());
    }

    private static LobbySnapshot snapshot(long revision, CanonicalHandle handle) {
        return new LobbySnapshot(revision, List.of(new LobbyMember(PLAYER_ID, handle)));
    }

    private static ProtocolEnvelope snapshotEnvelope(LobbySnapshot snapshot, long sequence) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT,
                MessageType.LOBBY_SNAPSHOT,
                new UUID(1L, 2L),
                sequence,
                LobbyProtocolCodec.encodeSnapshot(snapshot));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
    }

    private static final class StubAuthenticatedSession implements AuthenticatedReliableSession {
        private final UUID sessionId = new UUID(1L, 2L);
        private final ServerId serverId = new ServerId("sfs1_" + "b".repeat(52));
        private final StubReliableChannel channel = new StubReliableChannel();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public ServerId serverId() {
            return serverId;
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
            closeCount.incrementAndGet();
            return channel.close();
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final ArrayDeque<Optional<ProtocolEnvelope>> pending = new ArrayDeque<>();
        private final ArrayDeque<CompletableFuture<Optional<ProtocolEnvelope>>> waiters =
                new ArrayDeque<>();
        private final AtomicBoolean open = new AtomicBoolean(true);

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
            open.set(false);
            deliver(Optional.empty());
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
    }
}
