package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumSet;
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
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
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
            assertThat(snapshot.members().getFirst().team()).isEqualTo(LobbyTeam.UNASSIGNED);
            assertThat(snapshot.members().getFirst().ready()).isFalse();
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

            LobbySnapshot bravoSnapshot = latestSnapshot(bravo);
            LobbySnapshot alphaSnapshot = latestSnapshot(alpha);

            assertThat(bravoSnapshot).isEqualTo(alphaSnapshot);
            assertThat(bravoSnapshot.revision()).isEqualTo(2L);
            assertThat(bravoSnapshot.members())
                    .extracting(LobbyMember::playerId)
                    .containsExactly(alpha.playerId(), bravo.playerId());
            assertThat(bravoSnapshot.members())
                    .allMatch(member -> member.team() == LobbyTeam.UNASSIGNED && !member.ready());
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
    void appliesTeamAndReadyCommandsAndBroadcastsAuthoritativeRoster()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);

            sendSelect(alpha, 1L, LobbyTeam.GREEN);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 3L, LobbyCommandOutcome.APPLIED);

            sendSelect(bravo, 1L, LobbyTeam.BLUE);
            waitForResult(bravo, 1L);
            assertResult(bravo, 1L, 4L, LobbyCommandOutcome.APPLIED);

            sendReady(alpha, 2L, true);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 5L, LobbyCommandOutcome.APPLIED);

            sendReady(bravo, 2L, true);
            waitForResult(bravo, 2L);
            assertResult(bravo, 2L, 6L, LobbyCommandOutcome.APPLIED);
            waitUntil(
                    () ->
                            latestSnapshotUnchecked(alpha).revision() == 6L
                                    && latestSnapshotUnchecked(bravo).revision() == 6L);

            LobbySnapshot snapshot = latestSnapshot(alpha);
            assertThat(snapshot).isEqualTo(latestSnapshot(bravo));
            assertThat(snapshot.members())
                    .containsExactly(
                            new LobbyMember(
                                    alpha.playerId(), alpha.handle(), LobbyTeam.GREEN, true),
                            new LobbyMember(
                                    bravo.playerId(), bravo.handle(), LobbyTeam.BLUE, true));
            assertThat(lobby.revision()).isEqualTo(6L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void reportsRejectedAndIdempotentCommandsWithoutFalseRevisionOrBroadcast()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, alpha);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);
            int initialSnapshots = snapshotCount(alpha);

            sendReady(alpha, 1L, true);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 1L, LobbyCommandOutcome.TEAM_REQUIRED);
            assertThat(snapshotCount(alpha)).isEqualTo(initialSnapshots);

            sendReady(alpha, 2L, false);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 1L, LobbyCommandOutcome.NO_CHANGE);
            assertThat(snapshotCount(alpha)).isEqualTo(initialSnapshots);

            sendSelect(alpha, 3L, LobbyTeam.GREEN);
            waitForResult(alpha, 3L);
            assertResult(alpha, 3L, 2L, LobbyCommandOutcome.APPLIED);
            waitUntil(() -> latestSnapshotUnchecked(alpha).revision() == 2L);
            int afterApplied = snapshotCount(alpha);

            sendSelect(alpha, 4L, LobbyTeam.GREEN);
            waitForResult(alpha, 4L);
            assertResult(alpha, 4L, 2L, LobbyCommandOutcome.NO_CHANGE);
            assertThat(snapshotCount(alpha)).isEqualTo(afterApplied);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void mapsDisabledFullAndImbalancedTeamsWithoutMutatingTheRoster() throws InterruptedException {
        LobbyConfiguration configuration =
                new LobbyConfiguration(EnumSet.of(TeamId.GREEN, TeamId.BLUE), 2, 1, 2);
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue, configuration);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);

            sendSelect(alpha, 1L, LobbyTeam.RED);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 2L, LobbyCommandOutcome.TEAM_DISABLED);

            sendSelect(alpha, 2L, LobbyTeam.GREEN);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 3L, LobbyCommandOutcome.APPLIED);

            sendSelect(bravo, 1L, LobbyTeam.GREEN);
            waitForResult(bravo, 1L);
            assertResult(bravo, 1L, 3L, LobbyCommandOutcome.TEAM_FULL);

            sendSelect(bravo, 2L, LobbyTeam.BLUE);
            waitForResult(bravo, 2L);
            assertResult(bravo, 2L, 4L, LobbyCommandOutcome.APPLIED);

            sendSelect(alpha, 3L, LobbyTeam.BLUE);
            waitForResult(alpha, 3L);
            assertResult(alpha, 3L, 4L, LobbyCommandOutcome.TEAM_FULL);
            assertThat(lobby.revision()).isEqualTo(4L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void replayedRequestIdFailsClosedAndRemovesOnlyTheOffendingSession()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            sendSelect(alpha, 1L, LobbyTeam.GREEN);
            waitForResult(alpha, 1L);

            sendReady(alpha, 1L, false);
            waitUntil(() -> lobby.memberCount() == 1 && alpha.closeCount() == 1);

            assertThat(bravo.closeCount()).isZero();
            assertThat(latestSnapshotUnchecked(bravo).members())
                    .extracting(LobbyMember::playerId)
                    .containsExactly(bravo.playerId());
            assertThat(lobby.revision()).isEqualTo(4L);
        } finally {
            lobby.close();
            queue.close();
        }
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
    void unexpectedClientMessageFailsClosed() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, transport);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);
            transport.channel.completeMessage(envelope(transport, MessageType.PING, new byte[0]));

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

    private static MinimalLobbyRuntime lobby(
            AuthorizedPlayerSessionQueue queue, LobbyConfiguration configuration) {
        return new MinimalLobbyRuntime(
                queue, configuration, Duration.ofSeconds(1), Duration.ofSeconds(2), ignored -> {});
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

    private static void sendSelect(TestSession session, long requestId, LobbyTeam team) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.LOBBY_SELECT_TEAM,
                        LobbyProtocolCodec.encodeSelectTeam(
                                new LobbySelectTeamCommand(requestId, team))));
    }

    private static void sendReady(TestSession session, long requestId, boolean ready) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.LOBBY_SET_READY,
                        LobbyProtocolCodec.encodeSetReady(
                                new LobbySetReadyCommand(requestId, ready))));
    }

    private static ProtocolEnvelope envelope(
            TestSession session, MessageType messageType, byte[] payload) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT, messageType, session.sessionId(), 0L, payload);
    }

    private static void waitForResult(TestSession session, long requestId)
            throws InterruptedException {
        waitUntil(
                () ->
                        commandResultsUnchecked(session).stream()
                                .anyMatch(result -> result.requestId() == requestId));
    }

    private static void assertResult(
            TestSession session, long requestId, long revision, LobbyCommandOutcome outcome) {
        assertThat(commandResultsUnchecked(session))
                .contains(new LobbyCommandResult(requestId, revision, outcome));
    }

    private static List<LobbyCommandResult> commandResultsUnchecked(TestSession session) {
        return session.channel.sent().stream()
                .filter(message -> message.messageType() == MessageType.LOBBY_COMMAND_RESULT)
                .map(
                        message -> {
                            try {
                                return LobbyProtocolCodec.decodeCommandResult(message.payload());
                            } catch (LobbyProtocolException exception) {
                                throw new AssertionError(exception);
                            }
                        })
                .toList();
    }

    private static int snapshotCount(TestSession session) {
        return Math.toIntExact(
                session.channel.sent().stream()
                        .filter(message -> message.messageType() == MessageType.LOBBY_SNAPSHOT)
                        .count());
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

    private static LobbySnapshot latestSnapshot(TestSession session) throws LobbyProtocolException {
        return LobbyProtocolCodec.decodeSnapshot(
                latestSnapshotMessage(session).orElseThrow().payload());
    }

    private static LobbySnapshot latestSnapshotUnchecked(TestSession session) {
        try {
            return latestSnapshot(session);
        } catch (LobbyProtocolException exception) {
            throw new AssertionError(exception);
        }
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
        private final ArrayDeque<Optional<ProtocolEnvelope>> inbound = new ArrayDeque<>();
        private final Object receiveLock = new Object();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger sequences = new AtomicInteger();
        private CompletableFuture<Optional<ProtocolEnvelope>> pendingReceive;

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
            synchronized (receiveLock) {
                if (!inbound.isEmpty()) {
                    return CompletableFuture.completedFuture(inbound.removeFirst());
                }
                if (!open.get()) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                if (pendingReceive != null) {
                    throw new IllegalStateException("only one receive may be active");
                }
                pendingReceive = new CompletableFuture<>();
                return pendingReceive.minimalCompletionStage();
            }
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            CompletableFuture<Optional<ProtocolEnvelope>> receiveToComplete;
            synchronized (receiveLock) {
                open.set(false);
                receiveToComplete = pendingReceive;
                pendingReceive = null;
                inbound.clear();
            }
            if (receiveToComplete != null) {
                receiveToComplete.complete(Optional.empty());
            }
            return CompletableFuture.completedFuture(null);
        }

        private List<SentMessage> sent() {
            return List.copyOf(sent);
        }

        private void completeEof() {
            completeInbound(Optional.empty());
        }

        private void completeMessage(ProtocolEnvelope message) {
            completeInbound(Optional.of(message));
        }

        private void completeInbound(Optional<ProtocolEnvelope> message) {
            CompletableFuture<Optional<ProtocolEnvelope>> receiveToComplete;
            synchronized (receiveLock) {
                receiveToComplete = pendingReceive;
                pendingReceive = null;
                if (receiveToComplete == null) {
                    inbound.addLast(message);
                    return;
                }
            }
            receiveToComplete.complete(message);
        }
    }
}
