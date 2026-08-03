package pl.grzegorz2047.standalonethewalls.server.lobby;

import java.time.Duration;
import java.util.ArrayList;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterCommand;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterDecision;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterRejection;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterRules;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchConfiguration;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSession;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSessionQueue;

/**
 * Owns reliable lobby membership, the authoritative roster, and the lobby-to-preparation match
 * phase.
 *
 * <p>The coordinator is the only state writer. Receive workers decode bounded payloads and enqueue
 * trusted-session intents. The simulation thread only advances a constant-memory tick mailbox; no
 * queue polling, network I/O, domain transition, or session close runs on that thread, the listener
 * accept thread, or an identity-admission worker.
 */
public final class MinimalLobbyRuntime implements AutoCloseable {
    private static final AtomicLong RUNTIME_IDS = new AtomicLong();
    private static final Duration MAXIMUM_SEND_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAXIMUM_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_TICK_RATE = 20;
    private static final long POLL_MILLIS = 10L;

    private final AuthorizedPlayerSessionQueue source;
    private final LobbyConfiguration configuration;
    private final LobbyMatchCoordinator matchCoordinator;
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
    private final AtomicReference<LobbyMatchSnapshot> visibleMatchSnapshot;
    private final AtomicLong offeredSimulationTick =
            new AtomicLong(LobbyMatchSnapshot.BEFORE_FIRST_TICK);
    private final AtomicBoolean tickSignalQueued = new AtomicBoolean();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final Object lifecycleLock = new Object();
    private final long runtimeId = RUNTIME_IDS.incrementAndGet();

    private Thread coordinator;

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver) {
        this(
                source,
                LobbyConfiguration.standard(),
                MatchConfiguration.defaults(DEFAULT_TICK_RATE),
                sendTimeout,
                shutdownTimeout,
                eventObserver,
                () -> {});
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver,
            Runnable terminalFailureAction) {
        this(
                source,
                LobbyConfiguration.standard(),
                MatchConfiguration.defaults(DEFAULT_TICK_RATE),
                sendTimeout,
                shutdownTimeout,
                eventObserver,
                terminalFailureAction);
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            LobbyConfiguration configuration,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver) {
        this(
                source,
                configuration,
                MatchConfiguration.defaults(DEFAULT_TICK_RATE),
                sendTimeout,
                shutdownTimeout,
                eventObserver,
                () -> {});
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            LobbyConfiguration configuration,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver,
            Runnable terminalFailureAction) {
        this(
                source,
                configuration,
                MatchConfiguration.defaults(DEFAULT_TICK_RATE),
                sendTimeout,
                shutdownTimeout,
                eventObserver,
                terminalFailureAction);
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            LobbyConfiguration configuration,
            MatchConfiguration matchConfiguration,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver) {
        this(
                source,
                configuration,
                matchConfiguration,
                sendTimeout,
                shutdownTimeout,
                eventObserver,
                () -> {});
    }

    public MinimalLobbyRuntime(
            AuthorizedPlayerSessionQueue source,
            LobbyConfiguration configuration,
            MatchConfiguration matchConfiguration,
            Duration sendTimeout,
            Duration shutdownTimeout,
            Consumer<MinimalLobbyEvent> eventObserver,
            Runnable terminalFailureAction) {
        this.source = Objects.requireNonNull(source, "source");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        MatchConfiguration lifecycleConfiguration =
                Objects.requireNonNull(matchConfiguration, "matchConfiguration");
        if (source.capacity() > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new IllegalArgumentException("source capacity exceeds minimal lobby capacity");
        }
        if (source.capacity() > configuration.maximumPlayers()) {
            throw new IllegalArgumentException("source capacity exceeds lobby configuration");
        }
        matchCoordinator = new LobbyMatchCoordinator(configuration, lifecycleConfiguration);
        visibleMatchSnapshot = new AtomicReference<>(matchCoordinator.snapshot());
        this.sendTimeout = requireDuration(sendTimeout, "sendTimeout", MAXIMUM_SEND_TIMEOUT);
        this.shutdownTimeout =
                requireDuration(shutdownTimeout, "shutdownTimeout", MAXIMUM_SHUTDOWN_TIMEOUT);
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
        this.terminalFailureAction =
                Objects.requireNonNull(terminalFailureAction, "terminalFailureAction");
        commands = new ArrayBlockingQueue<>(Math.max(16, source.capacity() * 4 + 2));
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

    public LobbyMatchSnapshot matchSnapshot() {
        return visibleMatchSnapshot.get();
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure.get());
    }

    /**
     * Offers one sequential simulation tick without blocking the caller.
     *
     * @return {@code false} when the runtime is not running or its bounded command queue cannot be
     *     signalled
     */
    public boolean offerSimulationTick(long tickNumber) {
        if (tickNumber < 0L) {
            throw new IllegalArgumentException("tickNumber cannot be negative");
        }
        if (lifecycle.get() != State.RUNNING) {
            return false;
        }
        while (true) {
            long previous = offeredSimulationTick.get();
            if (tickNumber < previous) {
                throw new IllegalArgumentException("tickNumber cannot move backwards");
            }
            if (tickNumber == previous) {
                return true;
            }
            if (tickNumber != Math.addExact(previous, 1L)) {
                throw new IllegalArgumentException("simulation tick gap is not allowed");
            }
            if (offeredSimulationTick.compareAndSet(previous, tickNumber)) {
                break;
            }
        }
        if (tickSignalQueued.compareAndSet(false, true)
                && !commands.offer(SimulationTickSignal.INSTANCE)) {
            tickSignalQueued.set(false);
            return false;
        }
        return true;
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
                if (command instanceof ClientCommand clientCommand) {
                    handleClientCommand(state, clientCommand);
                } else if (command instanceof SessionEnded ended) {
                    handleSessionEnded(state, ended);
                } else if (command == SimulationTickSignal.INSTANCE) {
                    handleSimulationTicks(state);
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
                if (!workers.awaitTermination(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
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

    private void handleSimulationTicks(LobbyState state) {
        while (true) {
            long targetTick = offeredSimulationTick.get();
            long nextTick = Math.addExact(matchCoordinator.snapshot().authoritativeTick(), 1L);
            while (nextTick <= targetTick) {
                Optional<LobbyMatchSnapshot> changed = matchCoordinator.advanceTick(nextTick);
                if (changed.isPresent()) {
                    visibleMatchSnapshot.set(changed.orElseThrow());
                    stabilizeMatchSnapshots(state);
                }
                nextTick = Math.addExact(nextTick, 1L);
            }

            tickSignalQueued.set(false);
            if (offeredSimulationTick.get() == targetTick
                    || !tickSignalQueued.compareAndSet(false, true)) {
                return;
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
        LobbyParticipantId participantId = participantId(session);
        if (state.members.containsKey(participantId)) {
            closeSession(session);
            publish(MinimalLobbyEvent.Code.DUPLICATE_PLAYER_REJECTED);
            return;
        }

        LobbyRosterDecision decision =
                LobbyRosterRules.apply(
                        configuration, state.roster, new LobbyRosterCommand.Join(participantId));
        if (!decision.accepted()) {
            closeSession(session);
            publish(MinimalLobbyEvent.Code.DUPLICATE_PLAYER_REJECTED);
            return;
        }

        LobbyMember identity = new LobbyMember(session.playerId(), session.handle());
        try {
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.LOBBY_JOINED,
                                    LobbyProtocolCodec.encodeJoined(
                                            new LobbyJoined(
                                                    decision.state().revision(), identity))),
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

        state.members.put(participantId, new MemberState(participantId, identity, session));
        commitRoster(state, decision.state());
        publish(MinimalLobbyEvent.Code.MEMBER_JOINED);
        stabilizeSnapshots(state);
        MemberState retained = state.members.get(participantId);
        if (retained != null && retained.session == session) {
            startReceiveWatcher(retained);
        }
    }

    private void handleClientCommand(LobbyState state, ClientCommand inbound) {
        MemberState member = state.members.get(inbound.participantId());
        if (member == null || !member.session.sessionId().equals(inbound.sessionId())) {
            return;
        }
        if (inbound.requestId() <= member.lastRequestId) {
            removeMember(state, member, EndReason.PROTOCOL_VIOLATION, true);
            return;
        }
        member.lastRequestId = inbound.requestId();

        if (!matchCoordinator.acceptsLobbyCommands()) {
            LobbyCommandResult locked =
                    new LobbyCommandResult(
                            inbound.requestId(),
                            state.roster.revision(),
                            LobbyCommandOutcome.MATCH_ALREADY_STARTED);
            if (!sendCommandResult(member, locked)) {
                removeMember(state, member, EndReason.SEND_FAILED, true);
            }
            return;
        }

        LobbyRosterDecision decision =
                LobbyRosterRules.apply(configuration, state.roster, inbound.command());
        boolean changed = decision.accepted() && decision.state() != state.roster;
        LobbyCommandOutcome outcome;
        if (decision.accepted()) {
            outcome = changed ? LobbyCommandOutcome.APPLIED : LobbyCommandOutcome.NO_CHANGE;
            if (changed) {
                commitRoster(state, decision.state());
            }
        } else {
            outcome = outcome(decision.rejection().orElseThrow());
        }

        LobbyCommandResult result =
                new LobbyCommandResult(inbound.requestId(), state.roster.revision(), outcome);
        if (!sendCommandResult(member, result)) {
            removeMember(state, member, EndReason.SEND_FAILED, true);
            return;
        }
        if (changed) {
            stabilizeSnapshots(state);
        }
    }

    private void handleSessionEnded(LobbyState state, SessionEnded ended) {
        MemberState member = state.members.get(ended.participantId());
        if (member == null || !member.session.sessionId().equals(ended.sessionId())) {
            return;
        }
        removeMember(state, member, ended.reason(), true);
    }

    private void removeMember(
            LobbyState state, MemberState member, EndReason reason, boolean broadcastSnapshot) {
        MemberState current = state.members.get(member.participantId);
        if (current == null || current.session != member.session) {
            return;
        }
        state.members.remove(member.participantId);
        LobbyRosterDecision decision =
                LobbyRosterRules.apply(
                        configuration,
                        state.roster,
                        new LobbyRosterCommand.Leave(member.participantId));
        if (!decision.accepted()) {
            throw new IllegalStateException("authoritative lobby roster rejected an owned leave");
        }
        commitRoster(state, decision.state());
        closeSession(member.session);
        publish(eventCode(reason));
        if (broadcastSnapshot) {
            stabilizeSnapshots(state);
        }
    }

    private void stabilizeSnapshots(LobbyState state) {
        while (!state.members.isEmpty()) {
            byte[] rosterPayload = LobbyProtocolCodec.encodeSnapshot(snapshot(state));
            List<LobbyParticipantId> rosterFailed =
                    sendToMembers(state.members, MessageType.LOBBY_SNAPSHOT, rosterPayload);
            if (!rosterFailed.isEmpty()) {
                removeFailedMembers(state, rosterFailed);
                continue;
            }

            byte[] matchPayload =
                    LobbyMatchProtocolCodec.encodeSnapshot(
                            LobbyMatchProtocolAdapter.toProtocol(matchCoordinator.snapshot()));
            List<LobbyParticipantId> matchFailed =
                    sendToMembers(state.members, MessageType.LOBBY_MATCH_SNAPSHOT, matchPayload);
            if (matchFailed.isEmpty()) {
                return;
            }
            removeFailedMembers(state, matchFailed);
        }
    }

    private void stabilizeMatchSnapshots(LobbyState state) {
        if (state.members.isEmpty()) {
            return;
        }
        byte[] payload =
                LobbyMatchProtocolCodec.encodeSnapshot(
                        LobbyMatchProtocolAdapter.toProtocol(matchCoordinator.snapshot()));
        List<LobbyParticipantId> failed =
                sendToMembers(state.members, MessageType.LOBBY_MATCH_SNAPSHOT, payload);
        if (failed.isEmpty()) {
            return;
        }
        removeFailedMembers(state, failed);
        stabilizeSnapshots(state);
    }

    private void removeFailedMembers(LobbyState state, List<LobbyParticipantId> failed) {
        failed.stream()
                .sorted()
                .forEach(
                        participantId -> {
                            MemberState removed = state.members.get(participantId);
                            if (removed != null) {
                                removeMember(state, removed, EndReason.SEND_FAILED, false);
                            }
                        });
    }

    private LobbySnapshot snapshot(LobbyState state) {
        List<LobbyMember> members = new ArrayList<>(state.roster.participants().size());
        for (LobbyParticipantState participant : state.roster.participants()) {
            MemberState member = state.members.get(participant.participantId());
            if (member == null) {
                throw new IllegalStateException("authoritative roster contains an unowned member");
            }
            members.add(
                    new LobbyMember(
                            member.identity.playerId(),
                            member.identity.handle(),
                            participant
                                    .team()
                                    .map(MinimalLobbyRuntime::protocolTeam)
                                    .orElse(LobbyTeam.UNASSIGNED),
                            participant.ready()));
        }
        return new LobbySnapshot(state.roster.revision(), members);
    }

    private List<LobbyParticipantId> sendToMembers(
            Map<LobbyParticipantId, MemberState> members, MessageType messageType, byte[] payload) {
        Map<LobbyParticipantId, CompletableFuture<ReliableSendResult>> sends =
                new LinkedHashMap<>();
        List<LobbyParticipantId> failed = new ArrayList<>();
        for (Map.Entry<LobbyParticipantId, MemberState> entry : members.entrySet()) {
            try {
                sends.put(
                        entry.getKey(),
                        Objects.requireNonNull(
                                        entry.getValue()
                                                .session
                                                .reliableChannel()
                                                .send(messageType, payload),
                                        "snapshot send stage")
                                .toCompletableFuture());
            } catch (RuntimeException exception) {
                failed.add(entry.getKey());
            }
        }

        long deadline = System.nanoTime() + sendTimeout.toNanos();
        for (Map.Entry<LobbyParticipantId, CompletableFuture<ReliableSendResult>> entry :
                sends.entrySet()) {
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
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                entry.getValue().cancel(true);
                failed.add(entry.getKey());
            }
        }
        return List.copyOf(failed);
    }

    private boolean sendCommandResult(MemberState member, LobbyCommandResult result) {
        try {
            await(
                    member.session
                            .reliableChannel()
                            .send(
                                    MessageType.LOBBY_COMMAND_RESULT,
                                    LobbyProtocolCodec.encodeCommandResult(result)),
                    sendTimeout);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            return false;
        }
    }

    private void startReceiveWatcher(MemberState member) {
        try {
            workers.execute(() -> runReceiveWatcher(member));
        } catch (RejectedExecutionException exception) {
            enqueue(
                    new SessionEnded(
                            member.participantId,
                            member.session.sessionId(),
                            EndReason.RECEIVE_FAILED));
        }
    }

    private void runReceiveWatcher(MemberState member) {
        try {
            while (lifecycle.get() == State.RUNNING) {
                Optional<ProtocolEnvelope> received =
                        Objects.requireNonNull(
                                        member.session.reliableChannel().receive(),
                                        "lobby receive stage")
                                .toCompletableFuture()
                                .get();
                if (received.isEmpty()) {
                    enqueue(
                            new SessionEnded(
                                    member.participantId,
                                    member.session.sessionId(),
                                    EndReason.EOF));
                    return;
                }
                ClientCommand command = decodeClientCommand(member, received.orElseThrow());
                enqueue(command);
            }
        } catch (LobbyProtocolException exception) {
            enqueue(
                    new SessionEnded(
                            member.participantId,
                            member.session.sessionId(),
                            EndReason.PROTOCOL_VIOLATION));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException exception) {
            enqueue(
                    new SessionEnded(
                            member.participantId,
                            member.session.sessionId(),
                            EndReason.RECEIVE_FAILED));
        }
    }

    private static ClientCommand decodeClientCommand(MemberState member, ProtocolEnvelope envelope)
            throws LobbyProtocolException {
        return switch (envelope.messageType()) {
            case LOBBY_SELECT_TEAM -> {
                LobbySelectTeamCommand command =
                        LobbyProtocolCodec.decodeSelectTeam(envelope.payload());
                yield new ClientCommand(
                        member.participantId,
                        member.session.sessionId(),
                        command.requestId(),
                        new LobbyRosterCommand.SelectTeam(
                                member.participantId, domainTeam(command.team())));
            }
            case LOBBY_SET_READY -> {
                LobbySetReadyCommand command =
                        LobbyProtocolCodec.decodeSetReady(envelope.payload());
                yield new ClientCommand(
                        member.participantId,
                        member.session.sessionId(),
                        command.requestId(),
                        new LobbyRosterCommand.SetReady(member.participantId, command.ready()));
            }
            default ->
                    throw new LobbyProtocolException(
                            LobbyProtocolException.Code.INVALID_SIZE,
                            "message type is not accepted by the lobby command boundary");
        };
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
        List<MemberState> members = new ArrayList<>(state.members.values());
        List<AuthorizedPlayerSession> sessions = new ArrayList<>(members.size());
        for (MemberState member : members) {
            MemberState removed = state.members.remove(member.participantId);
            if (removed == null || removed.session != member.session) {
                throw new IllegalStateException("owned lobby member disappeared during shutdown");
            }
            LobbyRosterDecision decision =
                    LobbyRosterRules.apply(
                            configuration,
                            state.roster,
                            new LobbyRosterCommand.Leave(member.participantId));
            if (!decision.accepted()) {
                throw new IllegalStateException(
                        "authoritative lobby roster rejected an owned shutdown leave");
            }
            commitRoster(state, decision.state());
            sessions.add(member.session);
        }
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

    private void commitRoster(LobbyState state, LobbyRosterState roster) {
        state.roster = Objects.requireNonNull(roster, "roster");
        memberCount.set(roster.participants().size());
        visibleRevision.set(roster.revision());
        matchCoordinator.updateRoster(roster).ifPresent(visibleMatchSnapshot::set);
    }

    private void publish(MinimalLobbyEvent.Code code) {
        try {
            eventObserver.accept(
                    new MinimalLobbyEvent(code, memberCount.get(), visibleRevision.get()));
        } catch (RuntimeException ignored) {
            // Diagnostic observers cannot control membership or lifecycle.
        }
    }

    private static LobbyParticipantId participantId(AuthorizedPlayerSession session) {
        return new LobbyParticipantId(session.playerId().value());
    }

    private static TeamId domainTeam(LobbyTeam team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> TeamId.GREEN;
            case BLUE -> TeamId.BLUE;
            case RED -> TeamId.RED;
            case YELLOW -> TeamId.YELLOW;
            case UNASSIGNED -> throw new IllegalArgumentException("unassigned is not selectable");
        };
    }

    private static LobbyTeam protocolTeam(TeamId team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> LobbyTeam.GREEN;
            case BLUE -> LobbyTeam.BLUE;
            case RED -> LobbyTeam.RED;
            case YELLOW -> LobbyTeam.YELLOW;
        };
    }

    private static LobbyCommandOutcome outcome(LobbyRosterRejection rejection) {
        return switch (Objects.requireNonNull(rejection, "rejection")) {
            case LOBBY_FULL -> LobbyCommandOutcome.LOBBY_FULL;
            case DUPLICATE_PARTICIPANT -> LobbyCommandOutcome.DUPLICATE_PARTICIPANT;
            case UNKNOWN_PARTICIPANT -> LobbyCommandOutcome.UNKNOWN_PARTICIPANT;
            case TEAM_DISABLED -> LobbyCommandOutcome.TEAM_DISABLED;
            case TEAM_FULL -> LobbyCommandOutcome.TEAM_FULL;
            case TEAM_IMBALANCE -> LobbyCommandOutcome.TEAM_IMBALANCE;
            case TEAM_REQUIRED -> LobbyCommandOutcome.TEAM_REQUIRED;
        };
    }

    private static MinimalLobbyEvent.Code eventCode(EndReason reason) {
        return switch (reason) {
            case EOF -> MinimalLobbyEvent.Code.MEMBER_LEFT;
            case PROTOCOL_VIOLATION -> MinimalLobbyEvent.Code.PROTOCOL_VIOLATION;
            case SEND_FAILED -> MinimalLobbyEvent.Code.SEND_FAILED;
            case RECEIVE_FAILED -> MinimalLobbyEvent.Code.RECEIVE_FAILED;
        };
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
        SEND_FAILED,
        RECEIVE_FAILED
    }

    private sealed interface Command
            permits ClientCommand, SessionEnded, SimulationTickSignal, Shutdown {}

    private record ClientCommand(
            LobbyParticipantId participantId,
            UUID sessionId,
            long requestId,
            LobbyRosterCommand command)
            implements Command {
        private ClientCommand {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(command, "command");
            if (requestId < 1L) {
                throw new IllegalArgumentException("requestId must be positive");
            }
            if (!participantId.equals(command.participantId())) {
                throw new IllegalArgumentException("command identity does not match its session");
            }
        }
    }

    private record SessionEnded(LobbyParticipantId participantId, UUID sessionId, EndReason reason)
            implements Command {
        private SessionEnded {
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private enum SimulationTickSignal implements Command {
        INSTANCE
    }

    private enum Shutdown implements Command {
        INSTANCE
    }

    private static final class MemberState {
        private final LobbyParticipantId participantId;
        private final LobbyMember identity;
        private final AuthorizedPlayerSession session;
        private long lastRequestId;

        private MemberState(
                LobbyParticipantId participantId,
                LobbyMember identity,
                AuthorizedPlayerSession session) {
            this.participantId = Objects.requireNonNull(participantId, "participantId");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.session = Objects.requireNonNull(session, "session");
        }
    }

    private static final class LobbyState {
        private final TreeMap<LobbyParticipantId, MemberState> members = new TreeMap<>();
        private LobbyRosterState roster = LobbyRosterState.initial();
    }
}
