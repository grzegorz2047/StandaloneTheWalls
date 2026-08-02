package pl.grzegorz2047.standalonethewalls.server.lobby;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSession;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSessionQueue;

/**
 * Owns the first reliable-only lobby membership runtime.
 *
 * <p>The coordinator and receive watchers use owned virtual threads. No queue polling, network
 * send/receive, or session close executes on the fixed-tick simulation thread, listener accept
 * thread, or identity-admission worker.
 */
public final class MinimalLobbyRuntime implements AutoCloseable {
    private static final AtomicLong RUNTIME_IDS = new AtomicLong();
    private static final Duration MAXIMUM_SEND_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAXIMUM_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_MILLIS = 10L;

    private final AuthorizedPlayerSessionQueue source;
    private final Duration sendTimeout;
    private final Duration shutdownTimeout;
    private final Consumer<MinimalLobbyEvent> eventObserver;
    private final Runnable terminalFailureAction;
    private final BlockingQueue<Command> commands;
    private final ExecutorService workers;
    private final AtomicReference<State> lifecycle = new AtomicReference<>(State.NEW);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger memberCount = new AtomicInteger();
    private final AtomicLong visibleRevision = new AtomicLong();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final Object lifecycleLock = new Object();
    private final long runtimeId = RUNTIME_IDS.incrementAndGet();

    private Thread coordinator;

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver) {
        this(source, sendTimeout, shutdownTimeout, eventObserver, () -> {});
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver,
            Runnable terminalFailureAction) {
        this.source = Objects.requireNonNull(source, "source");
        if (source.capacity() > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new IllegalArgumentException("source capacity exceeds minimal lobby capacity");
        }
        this.sendTimeout = requireDuration(sendTimeout, "sendTimeout", MAXIMUM_SEND_TIMEOUT);
        this.shutdownTimeout =
                requireDuration(shutdownTimeout, "shutdownTimeout", MAXIMUM_SHUTDOWN_TIMEOUT);
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
        this.terminalFailureAction =
                Objects.requireNonNull(terminalFailureAction, "terminalFailureAction");
        commands = new ArrayBlockingQueue<>(Math.max(16, source.capacity() * 4 + 1));
        workers =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual()
                                .name("sunderfront-minimal-lobby-worker-" + runtimeId + '-', 0L)
                                .factory());
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (!lifecycle.compareAndSet(State.NEW, State.RUNNING)) {
                throw new IllegalStateException("minimal lobby runtime can be started only once");
            }
            coordinator =
                    Thread.ofVirtual()
                            .name("sunderfront-minimal-lobby-coordinator-" + runtimeId)
                            .start(this::runCoordinator);
        }
    }

    public boolean isRunning() {
        return lifecycle.get() == State.RUNNING;
    }

    public int memberCount() {
        return memberCount.get();
    }

    public long revision() {
        return visibleRevision.get();
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure.get());
    }

    @Override
    public void close() {
        Thread coordinatorThread;
        synchronized (lifecycleLock) {
            State current = lifecycle.get();
            if (current == State.CLOSED) {
                return;
            }
            if (current == State.NEW) {
                lifecycle.set(State.CLOSED);
                workers.shutdownNow();
                terminated.complete(null);
                publish(MinimalLobbyEvent.Code.RUNTIME_CLOSED);
                return;
            }
            lifecycle.compareAndSet(State.RUNNING, State.CLOSING);
            coordinatorThread = coordinator;
            if (!commands.offer(Shutdown.INSTANCE) && coordinatorThread != null) {
                coordinatorThread.interrupt();
            }
        }

        if (Thread.currentThread() == coordinatorThread) {
            return;
        }
        try {
            terminated.get(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while closing minimal lobby", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("minimal lobby close failed", unwrap(exception));
        } catch (TimeoutException exception) {
            if (coordinatorThread != null) {
                coordinatorThread.interrupt();
            }
            throw new IllegalStateException(
                    "minimal lobby did not close within the bounded timeout", exception);
        }
    }

    private void runCoordinator() {
        LobbyState state = new LobbyState();
        Throwable terminalFailure = null;
        try {
            while (lifecycle.get() == State.RUNNING) {
                acceptPending(state);
                Command command = commands.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (command instanceof SessionEnded ended) {
                    handleSessionEnded(state, ended);
                } else if (command == Shutdown.INSTANCE) {
                    break;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (lifecycle.get() == State.RUNNING) {
                terminalFailure = exception;
                publish(MinimalLobbyEvent.Code.INTERNAL_FAILURE);
            }
        } catch (RuntimeException exception) {
            terminalFailure = exception;
            publish(MinimalLobbyEvent.Code.INTERNAL_FAILURE);
        } finally {
            lifecycle.set(State.CLOSING);
            try {
                closeMembers(state);
            } catch (RuntimeException closeFailure) {
                if (terminalFailure == null) {
                    terminalFailure = closeFailure;
                } else {
                    terminalFailure.addSuppressed(closeFailure);
                }
            }
            workers.shutdownNow();
            try {
                if (!workers.awaitTermination(
                        shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    IllegalStateException workerFailure =
                            new IllegalStateException("minimal lobby workers did not terminate");
                    if (terminalFailure == null) {
                        terminalFailure = workerFailure;
                    } else {
                        terminalFailure.addSuppressed(workerFailure);
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (terminalFailure == null) {
                    terminalFailure = exception;
                } else {
                    terminalFailure.addSuppressed(exception);
                }
            }
            lifecycle.set(State.CLOSED);
            publish(MinimalLobbyEvent.Code.RUNTIME_CLOSED);
            if (terminalFailure == null) {
                terminated.complete(null);
            } else {
                failure.compareAndSet(null, terminalFailure);
                try {
                    terminalFailureAction.run();
                } catch (RuntimeException actionFailure) {
                    terminalFailure.addSuppressed(actionFailure);
                }
                terminated.completeExceptionally(terminalFailure);
            }
        }
    }

    private void acceptPending(LobbyState state) {
        List<AuthorizedPlayerSession> pending = source.drain(source.capacity());
        for (AuthorizedPlayerSession session : pending) {
            if (lifecycle.get() != State.RUNNING) {
                closeSession(session);
                continue;
            }
            acceptOne(state, session);
        }
    }

    private void acceptOne(LobbyState state, AuthorizedPlayerSession session) {
        PlayerId playerId = session.playerId();
        if (state.members.containsKey(playerId)) {
            closeSession(session);
            publish(MinimalLobbyEvent.Code.DUPLICATE_PLAYER_REJECTED);
            return;
        }

        long joinedRevision = Math.incrementExact(state.revision);
        LobbyMember member = new LobbyMember(playerId, session.handle());
        try {
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.LOBBY_JOINED,
                                    LobbyProtocolCodec.encodeJoined(
                                            new LobbyJoined(joinedRevision, member))),
                    sendTimeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            closeSession(session);
            return;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            closeSession(session);
            publish(MinimalLobbyEvent.Code.SEND_FAILED);
            return;
        }

        state.revision = joinedRevision;
        visibleRevision.set(joinedRevision);
        state.members.put(playerId, new MemberState(member, session));
        memberCount.set(state.members.size());
        publish(MinimalLobbyEvent.Code.MEMBER_JOINED);
        stabilizeSnapshots(state);
        MemberState retained = state.members.get(playerId);
        if (retained != null && retained.session == session) {
            startReceiveWatcher(retained);
        }
    }

    private void handleSessionEnded(LobbyState state, SessionEnded ended) {
        MemberState current = state.members.get(ended.playerId);
        if (current == null || !current.session.sessionId().equals(ended.sessionId)) {
            return;
        }
        state.members.remove(ended.playerId);
        incrementRevision(state);
        memberCount.set(state.members.size());
        closeSession(current.session);
        publish(
                ended.reason == EndReason.PROTOCOL_VIOLATION
                        ? MinimalLobbyEvent.Code.PROTOCOL_VIOLATION
                        : ended.reason == EndReason.RECEIVE_FAILED
                                ? MinimalLobbyEvent.Code.RECEIVE_FAILED
                                : MinimalLobbyEvent.Code.MEMBER_LEFT);
        stabilizeSnapshots(state);
    }

    private void stabilizeSnapshots(LobbyState state) {
        while (!state.members.isEmpty()) {
            LobbySnapshot snapshot =
                    new LobbySnapshot(
                            state.revision,
                            state.members.values().stream()
                                    .map(memberState -> memberState.member)
                                    .toList());
            byte[] payload = LobbyProtocolCodec.encodeSnapshot(snapshot);
            List<PlayerId> failed = sendSnapshot(state.members, payload);
            if (failed.isEmpty()) {
                return;
            }
            failed.stream().sorted(Comparator.comparing(PlayerId::value)).forEach(playerId -> {
                MemberState removed = state.members.remove(playerId);
                if (removed != null) {
                    incrementRevision(state);
                    memberCount.set(state.members.size());
                    closeSession(removed.session);
                    publish(MinimalLobbyEvent.Code.SEND_FAILED);
                }
            });
        }
    }

    private List<PlayerId> sendSnapshot(
            Map<PlayerId, MemberState> members, byte[] payload) {
        Map<PlayerId, CompletableFuture<ReliableSendResult>> sends = new LinkedHashMap<>();
        List<PlayerId> failed = new ArrayList<>();
        for (Map.Entry<PlayerId, MemberState> entry : members.entrySet()) {
            try {
                sends.put(
                        entry.getKey(),
                        Objects.requireNonNull(
                                        entry.getValue()
                                                .session
                                                .reliableChannel()
                                                .send(MessageType.LOBBY_SNAPSHOT, payload),
                                        "snapshot send stage")
                                .toCompletableFuture());
            } catch (RuntimeException exception) {
                failed.add(entry.getKey());
            }
        }

        long deadline = System.nanoTime() + sendTimeout.toNanos();
        for (Map.Entry<PlayerId, CompletableFuture<ReliableSendResult>> entry : sends.entrySet()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                entry.getValue().cancel(true);
                failed.add(entry.getKey());
                continue;
            }
            try {
                entry.getValue().get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                entry.getValue().cancel(true);
                failed.add(entry.getKey());
            } catch (ExecutionException | TimeoutException exception) {
                entry.getValue().cancel(true);
                failed.add(entry.getKey());
            } catch (RuntimeException exception) {
                entry.getValue().cancel(true);
                failed.add(entry.getKey());
            }
        }
        return List.copyOf(failed);
    }

    private void startReceiveWatcher(MemberState member) {
        try {
            workers.execute(
                    () -> {
                        EndReason reason;
                        try {
                            Optional<ProtocolEnvelope> received =
                                    Objects.requireNonNull(
                                                    member.session.reliableChannel().receive(),
                                                    "lobby receive stage")
                                            .toCompletableFuture()
                                            .get();
                            reason =
                                    received.isPresent()
                                            ? EndReason.PROTOCOL_VIOLATION
                                            : EndReason.EOF;
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (ExecutionException exception) {
                            reason = EndReason.RECEIVE_FAILED;
                        } catch (RuntimeException exception) {
                            reason = EndReason.RECEIVE_FAILED;
                        }
                        enqueue(
                                new SessionEnded(
                                        member.member.playerId(),
                                        member.session.sessionId(),
                                        reason));
                    });
        } catch (RejectedExecutionException exception) {
            enqueue(
                    new SessionEnded(
                            member.member.playerId(),
                            member.session.sessionId(),
                            EndReason.RECEIVE_FAILED));
        }
    }

    private void enqueue(Command command) {
        while (lifecycle.get() == State.RUNNING) {
            try {
                if (commands.offer(command, POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void closeMembers(LobbyState state) {
        List<AuthorizedPlayerSession> sessions =
                state.members.values().stream().map(member -> member.session).toList();
        state.members.clear();
        memberCount.set(0);
        if (sessions.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> closures = new ArrayList<>(sessions.size());
        for (AuthorizedPlayerSession session : sessions) {
            try {
                closures.add(
                        Objects.requireNonNull(session.closeAsync(), "session close stage")
                                .toCompletableFuture());
            } catch (RuntimeException exception) {
                CompletableFuture<Void> failedClose = new CompletableFuture<>();
                failedClose.completeExceptionally(exception);
                closures.add(failedClose);
            }
        }
        awaitAll(closures, shutdownTimeout);
    }

    private void closeSession(AuthorizedPlayerSession session) {
        try {
            await(session.closeAsync(), sendTimeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            // Capacity release remains bound to eventual close completion; details stay private.
        }
    }

    private long incrementRevision(LobbyState state) {
        state.revision = Math.incrementExact(state.revision);
        visibleRevision.set(state.revision);
        return state.revision;
    }

    private void publish(MinimalLobbyEvent.Code code) {
        try {
            eventObserver.accept(
                    new MinimalLobbyEvent(code, memberCount.get(), visibleRevision.get()));
        } catch (RuntimeException ignored) {
            // Diagnostic observers cannot control membership or lifecycle.
        }
    }

    private static <T> T await(CompletionStage<T> stage, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return Objects.requireNonNull(stage, "stage")
                .toCompletableFuture()
                .get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void awaitAll(List<CompletableFuture<Void>> futures, Duration timeout) {
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new))
                    .get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while closing lobby sessions", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("lobby session close failed", unwrap(exception));
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "lobby sessions did not close within the bounded timeout", exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("lobby session close failed", exception);
        }
    }

    private static Duration requireDuration(Duration value, String field, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero()
                || duration.isNegative()
                || duration.compareTo(maximum) > 0
                || duration.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        return duration;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private enum State {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }

    private enum EndReason {
        EOF,
        PROTOCOL_VIOLATION,
        RECEIVE_FAILED
    }

    private sealed interface Command permits SessionEnded, Shutdown {}

    private record SessionEnded(PlayerId playerId, UUID sessionId, EndReason reason)
            implements Command {
        private SessionEnded {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private enum Shutdown implements Command {
        INSTANCE
    }

    private static final class MemberState {
        private final LobbyMember member;
        private final AuthorizedPlayerSession session;

        private MemberState(LobbyMember member, AuthorizedPlayerSession session) {
            this.member = Objects.requireNonNull(member, "member");
            this.session = Objects.requireNonNull(session, "session");
        }
    }

    private static final class LobbyState {
        private final TreeMap<PlayerId, MemberState> members =
                new TreeMap<>(Comparator.comparing(PlayerId::value));
        private long revision;
    }
}
