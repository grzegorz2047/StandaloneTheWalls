package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSessionTestFactory;

class ConnectedLobbyCommandTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId OTHER_PLAYER_ID = new PlayerId("sf1_" + "b".repeat(52));
    private static final CanonicalHandle HANDLE = new CanonicalHandle("alpha");
    private static final CanonicalHandle OTHER_HANDLE = new CanonicalHandle("bravo");
    private static final UUID SESSION_ID = UUID.fromString("12345678-1234-4234-8234-1234567890ab");

    @Test
    void submitsExactPayloadsWithMonotonicIdsAndOnlyOneCommandInFlight()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));

        LobbyCommandSubmission first = session.selectTeam(LobbyTeam.GREEN);
        assertEquals(LobbyCommandSubmissionStatus.SUBMITTED, first.status());
        LobbyCommandHandle firstHandle = first.handle().orElseThrow();
        assertEquals(1L, firstHandle.requestId());
        assertTrue(session.commandInFlight());

        LobbyCommandSubmission busy = session.setReady(true);
        assertEquals(LobbyCommandSubmissionStatus.COMMAND_IN_FLIGHT, busy.status());
        assertTrue(busy.handle().isEmpty());
        assertEquals(1, channel.sent().size());
        SentMessage selectMessage = channel.sent().getFirst();
        assertEquals(MessageType.LOBBY_SELECT_TEAM, selectMessage.messageType());
        assertEquals(
                new LobbySelectTeamCommand(1L, LobbyTeam.GREEN),
                LobbyProtocolCodec.decodeSelectTeam(selectMessage.payload()));

        LobbyCommandResult noChange = new LobbyCommandResult(1L, 1L, LobbyCommandOutcome.NO_CHANGE);
        channel.deliver(resultEnvelope(noChange, 1L));
        LobbyCommandResolution.Completed firstResolution = completed(firstHandle);
        assertEquals(noChange, firstResolution.result());
        assertEquals(snapshot(1L), firstResolution.snapshot());
        assertFalse(session.commandInFlight());
        assertTrue(session.isOpen());

        LobbyCommandSubmission second = session.setReady(true);
        assertEquals(LobbyCommandSubmissionStatus.SUBMITTED, second.status());
        LobbyCommandHandle secondHandle = second.handle().orElseThrow();
        assertEquals(2L, secondHandle.requestId());
        SentMessage readyMessage = channel.sent().get(1);
        assertEquals(MessageType.LOBBY_SET_READY, readyMessage.messageType());
        assertEquals(
                new LobbySetReadyCommand(2L, true),
                LobbyProtocolCodec.decodeSetReady(readyMessage.payload()));

        LobbyCommandResult rejected =
                new LobbyCommandResult(2L, 1L, LobbyCommandOutcome.TEAM_REQUIRED);
        channel.deliver(resultEnvelope(rejected, 2L));
        assertEquals(rejected, completed(secondHandle).result());
        assertTrue(session.isOpen());
        close(session);
    }

    @Test
    void appliedResultWaitsForItsExactSnapshotAfterEarlierRosterChange()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        LobbyCommandHandle handle = session.selectTeam(LobbyTeam.GREEN).handle().orElseThrow();

        LobbySnapshot unrelated =
                new LobbySnapshot(
                        2L,
                        List.of(
                                new LobbyMember(PLAYER_ID, HANDLE),
                                new LobbyMember(OTHER_PLAYER_ID, OTHER_HANDLE)));
        channel.deliver(snapshotEnvelope(unrelated, 1L));
        waitUntil(() -> session.currentSnapshot().revision() == 2L);
        assertFalse(handle.completion().toCompletableFuture().isDone());

        LobbyCommandResult result = new LobbyCommandResult(1L, 3L, LobbyCommandOutcome.APPLIED);
        channel.deliver(resultEnvelope(result, 2L));
        waitUntil(session::commandInFlight);
        assertFalse(handle.completion().toCompletableFuture().isDone());

        LobbySnapshot applied = snapshot(3L, LobbyTeam.GREEN, false);
        channel.deliver(snapshotEnvelope(applied, 3L));
        LobbyCommandResolution.Completed resolution = completed(handle);
        assertEquals(result, resolution.result());
        assertEquals(applied, resolution.snapshot());
        assertEquals(applied, session.currentSnapshot());
        assertFalse(session.commandInFlight());
        close(session);
    }

    @Test
    void everyNonAppliedOutcomeCompletesWithoutClosingOrChangingSnapshot()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(4L));
        List<LobbyCommandOutcome> outcomes =
                List.of(
                        LobbyCommandOutcome.NO_CHANGE,
                        LobbyCommandOutcome.LOBBY_FULL,
                        LobbyCommandOutcome.DUPLICATE_PARTICIPANT,
                        LobbyCommandOutcome.UNKNOWN_PARTICIPANT,
                        LobbyCommandOutcome.TEAM_DISABLED,
                        LobbyCommandOutcome.TEAM_FULL,
                        LobbyCommandOutcome.TEAM_IMBALANCE,
                        LobbyCommandOutcome.TEAM_REQUIRED);

        long requestId = 1L;
        for (LobbyCommandOutcome outcome : outcomes) {
            LobbyCommandHandle handle = session.setReady(false).handle().orElseThrow();
            assertEquals(requestId, handle.requestId());
            LobbyCommandResult result = new LobbyCommandResult(requestId, 4L, outcome);
            channel.deliver(resultEnvelope(result, requestId));
            LobbyCommandResolution.Completed resolution = completed(handle);
            assertEquals(result, resolution.result());
            assertEquals(snapshot(4L), resolution.snapshot());
            assertTrue(session.isOpen());
            requestId++;
        }
        close(session);
    }

    @Test
    void concurrentSubmissionsProduceOneCommandAndOneBusyResult()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LobbyCommandSubmission> first =
                    executor.submit(
                            () -> {
                                start.await();
                                return session.selectTeam(LobbyTeam.GREEN);
                            });
            Future<LobbyCommandSubmission> second =
                    executor.submit(
                            () -> {
                                start.await();
                                return session.setReady(true);
                            });
            start.countDown();

            List<LobbyCommandSubmissionStatus> statuses =
                    List.of(first.get().status(), second.get().status());
            assertEquals(
                    1,
                    statuses.stream()
                            .filter(status -> status == LobbyCommandSubmissionStatus.SUBMITTED)
                            .count());
            assertEquals(
                    1,
                    statuses.stream()
                            .filter(
                                    status ->
                                            status
                                                    == LobbyCommandSubmissionStatus
                                                            .COMMAND_IN_FLIGHT)
                            .count());
            assertEquals(1, channel.sent().size());
        }
        close(session);
    }

    @Test
    void notStartedAndClosedSessionsRejectSubmissionWithoutSending()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = createSession(channel, snapshot(1L));

        assertEquals(
                LobbyCommandSubmissionStatus.SESSION_CLOSED,
                session.selectTeam(LobbyTeam.BLUE).status());
        assertTrue(channel.sent().isEmpty());

        assertTrue(session.startReceiving());
        close(session);
        assertEquals(LobbyCommandSubmissionStatus.SESSION_CLOSED, session.setReady(true).status());
        assertTrue(channel.sent().isEmpty());
    }

    @Test
    void mismatchedRequestIdFailsPendingAndClosesSession()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        LobbyCommandHandle handle = session.setReady(true).handle().orElseThrow();

        channel.deliver(
                resultEnvelope(new LobbyCommandResult(2L, 1L, LobbyCommandOutcome.NO_CHANGE), 1L));

        assertTerminalFailure(
                session, handle, DirectConnectFailureCode.LOBBY_COMMAND_RESULT_UNEXPECTED);
        assertEquals(1, channel.closeCount());
    }

    @Test
    void duplicateResultAfterCompletionClosesSession()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        LobbyCommandHandle handle = session.setReady(false).handle().orElseThrow();
        LobbyCommandResult result = new LobbyCommandResult(1L, 1L, LobbyCommandOutcome.NO_CHANGE);

        channel.deliver(resultEnvelope(result, 1L));
        completed(handle);
        channel.deliver(resultEnvelope(result, 2L));

        Optional<DirectConnectFailure> failure = awaitTermination(session);
        assertEquals(
                DirectConnectFailureCode.LOBBY_COMMAND_RESULT_UNEXPECTED,
                failure.orElseThrow().code());
        assertEquals(1, channel.closeCount());
    }

    @Test
    void malformedResultFailsPendingWithoutExposingExceptionText()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        LobbyCommandHandle handle = session.setReady(true).handle().orElseThrow();
        channel.deliver(
                new ProtocolEnvelope(
                        ProtocolVersion.CURRENT,
                        MessageType.LOBBY_COMMAND_RESULT,
                        SESSION_ID,
                        1L,
                        new byte[] {1, 2, 3}));

        LobbyCommandResolution.Failed failed =
                assertTerminalFailure(
                        session, handle, DirectConnectFailureCode.LOBBY_COMMAND_RESULT_MALFORMED);
        assertTrue(failed.failure().admissionStatus().isEmpty());
        assertEquals(1, channel.closeCount());
    }

    @Test
    void inconsistentResultRevisionFailsClosed()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(5L));
        LobbyCommandHandle handle = session.selectTeam(LobbyTeam.GREEN).handle().orElseThrow();

        channel.deliver(
                resultEnvelope(new LobbyCommandResult(1L, 7L, LobbyCommandOutcome.APPLIED), 1L));

        assertTerminalFailure(
                session, handle, DirectConnectFailureCode.LOBBY_COMMAND_REVISION_MISMATCH);
    }

    @Test
    void snapshotSkippingAppliedRevisionFailsClosed()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));
        LobbyCommandHandle handle = session.selectTeam(LobbyTeam.GREEN).handle().orElseThrow();
        channel.deliver(
                resultEnvelope(new LobbyCommandResult(1L, 2L, LobbyCommandOutcome.APPLIED), 1L));
        channel.deliver(snapshotEnvelope(snapshot(3L, LobbyTeam.GREEN, false), 2L));

        assertTerminalFailure(
                session, handle, DirectConnectFailureCode.LOBBY_COMMAND_REVISION_MISMATCH);
    }

    @Test
    void sendFailureFailsPendingAndClosesExactlyOnce()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        channel.failNextSend(new IllegalStateException("private transport detail"));
        ConnectedLobbySession session = startedSession(channel, snapshot(1L));

        LobbyCommandHandle handle = session.setReady(true).handle().orElseThrow();

        LobbyCommandResolution.Failed failed =
                assertTerminalFailure(
                        session, handle, DirectConnectFailureCode.LOBBY_COMMAND_SEND_FAILED);
        assertEquals(DirectConnectFailureCode.LOBBY_COMMAND_SEND_FAILED, failed.failure().code());
        assertEquals(1, channel.closeCount());
        close(session);
        assertEquals(1, channel.closeCount());
    }

    @Test
    void eofAndReceiveFailureFinishPendingCommands()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel eofChannel = new StubReliableChannel();
        ConnectedLobbySession eofSession = startedSession(eofChannel, snapshot(1L));
        LobbyCommandHandle eofHandle = eofSession.setReady(true).handle().orElseThrow();
        eofChannel.deliverEof();
        assertTerminalFailure(eofSession, eofHandle, DirectConnectFailureCode.CONNECTION_CLOSED);

        StubReliableChannel failedChannel = new StubReliableChannel();
        ConnectedLobbySession failedSession = startedSession(failedChannel, snapshot(1L));
        LobbyCommandHandle failedHandle =
                failedSession.selectTeam(LobbyTeam.BLUE).handle().orElseThrow();
        failedChannel.failReceive(new IllegalStateException("private receive detail"));
        assertTerminalFailure(
                failedSession, failedHandle, DirectConnectFailureCode.CONNECTION_CLOSED);
    }

    @Test
    void manualCloseCancelsPendingWithoutRecordingTerminalFailure()
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        StubReliableChannel channel = new StubReliableChannel();
        AtomicInteger releases = new AtomicInteger();
        ConnectedLobbySession session =
                startedSession(channel, snapshot(1L), ignored -> releases.incrementAndGet());
        LobbyCommandHandle handle = session.setReady(true).handle().orElseThrow();

        close(session);
        LobbyCommandResolution.Failed failed = failed(handle);

        assertEquals(DirectConnectFailureCode.CANCELLED, failed.failure().code());
        assertTrue(session.terminalFailure().isEmpty());
        assertTrue(awaitTermination(session).isEmpty());
        assertEquals(1, releases.get());
        assertEquals(1, channel.closeCount());
    }

    private static ConnectedLobbySession startedSession(
            StubReliableChannel channel, LobbySnapshot initial) {
        return startedSession(channel, initial, ignored -> {});
    }

    private static ConnectedLobbySession startedSession(
            StubReliableChannel channel,
            LobbySnapshot initial,
            java.util.function.Consumer<ConnectedLobbySession> released) {
        ConnectedLobbySession session = createSession(channel, initial, released);
        assertTrue(session.startReceiving());
        return session;
    }

    private static ConnectedLobbySession createSession(
            StubReliableChannel channel, LobbySnapshot initial) {
        return createSession(channel, initial, ignored -> {});
    }

    private static ConnectedLobbySession createSession(
            StubReliableChannel channel,
            LobbySnapshot initial,
            java.util.function.Consumer<ConnectedLobbySession> released) {
        AuthenticatedReliableSession transport =
                AuthenticatedReliableSessionTestFactory.create(channel, PLAYER_ID, HANDLE);
        return new ConnectedLobbySession(transport, initial, released);
    }

    private static LobbySnapshot snapshot(long revision) {
        return snapshot(revision, LobbyTeam.UNASSIGNED, false);
    }

    private static LobbySnapshot snapshot(long revision, LobbyTeam team, boolean ready) {
        return new LobbySnapshot(
                revision, List.of(new LobbyMember(PLAYER_ID, HANDLE, team, ready)));
    }

    private static ProtocolEnvelope resultEnvelope(LobbyCommandResult result, long sequence) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT,
                MessageType.LOBBY_COMMAND_RESULT,
                SESSION_ID,
                sequence,
                LobbyProtocolCodec.encodeCommandResult(result));
    }

    private static ProtocolEnvelope snapshotEnvelope(LobbySnapshot snapshot, long sequence) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT,
                MessageType.LOBBY_SNAPSHOT,
                SESSION_ID,
                sequence,
                LobbyProtocolCodec.encodeSnapshot(snapshot));
    }

    private static LobbyCommandResolution.Completed completed(LobbyCommandHandle handle)
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        return assertInstanceOf(
                LobbyCommandResolution.Completed.class,
                handle.completion()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
    }

    private static LobbyCommandResolution.Failed failed(LobbyCommandHandle handle)
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        return assertInstanceOf(
                LobbyCommandResolution.Failed.class,
                handle.completion()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
    }

    private static LobbyCommandResolution.Failed assertTerminalFailure(
            ConnectedLobbySession session,
            LobbyCommandHandle handle,
            DirectConnectFailureCode expected)
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        LobbyCommandResolution.Failed failed = failed(handle);
        assertEquals(expected, failed.failure().code());
        assertEquals(expected, awaitTermination(session).orElseThrow().code());
        assertEquals(expected, session.terminalFailure().orElseThrow().code());
        assertFalse(session.isOpen());
        return failed;
    }

    private static Optional<DirectConnectFailure> awaitTermination(ConnectedLobbySession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        return session.termination()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void close(ConnectedLobbySession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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

    private record SentMessage(MessageType messageType, byte[] payload) {
        private SentMessage {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final ArrayDeque<Optional<ProtocolEnvelope>> pending = new ArrayDeque<>();
        private final ArrayDeque<CompletableFuture<Optional<ProtocolEnvelope>>> waiters =
                new ArrayDeque<>();
        private final java.util.concurrent.CopyOnWriteArrayList<SentMessage> sent =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger sequences = new AtomicInteger();
        private final AtomicReference<Throwable> nextSendFailure = new AtomicReference<>();
        private Throwable queuedReceiveFailure;

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            sent.add(new SentMessage(messageType, payload));
            Throwable failure = nextSendFailure.getAndSet(null);
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(
                    new ReliableSendResult(sequences.getAndIncrement()));
        }

        @Override
        public synchronized CompletionStage<Optional<ProtocolEnvelope>> receive() {
            if (queuedReceiveFailure != null) {
                Throwable failure = queuedReceiveFailure;
                queuedReceiveFailure = null;
                return CompletableFuture.failedFuture(failure);
            }
            Optional<ProtocolEnvelope> queued = pending.pollFirst();
            if (queued != null) {
                return CompletableFuture.completedFuture(queued);
            }
            if (!open.get()) {
                return CompletableFuture.completedFuture(Optional.empty());
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

        private List<SentMessage> sent() {
            return List.copyOf(sent);
        }

        private void failNextSend(Throwable failure) {
            nextSendFailure.set(failure);
        }

        private void deliver(ProtocolEnvelope envelope) {
            deliver(Optional.of(envelope));
        }

        private void deliverEof() {
            deliver(Optional.empty());
        }

        private synchronized void deliver(Optional<ProtocolEnvelope> envelope) {
            CompletableFuture<Optional<ProtocolEnvelope>> waiter = waiters.pollFirst();
            if (waiter == null) {
                pending.addLast(envelope);
            } else {
                waiter.complete(envelope);
            }
        }

        private synchronized void failReceive(Throwable failure) {
            CompletableFuture<Optional<ProtocolEnvelope>> waiter = waiters.pollFirst();
            if (waiter == null) {
                queuedReceiveFailure = failure;
            } else {
                waiter.completeExceptionally(failure);
            }
        }

        private int closeCount() {
            return closes.get();
        }
    }
}
