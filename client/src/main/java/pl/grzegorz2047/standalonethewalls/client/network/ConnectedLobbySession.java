package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoadException;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoader;
import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketRequest;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketResult;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;

/** Owns one admitted reliable session, strict snapshots, and one correlated lobby command. */
public final class ConnectedLobbySession implements AutoCloseable {
    private final AuthenticatedReliableSession session;
    private final PlayerId playerId;
    private final CanonicalHandle handle;
    private final AtomicReference<LobbySnapshot> snapshot;
    private final AtomicReference<LobbyMatchPhaseSnapshot> matchSnapshot;
    private final AtomicReference<PreparationState> preparationState = new AtomicReference<>();
    private final AtomicReference<DirectConnectFailure> terminalFailure = new AtomicReference<>();
    private final AtomicBoolean receiverStarted = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    private final CompletableFuture<Optional<DirectConnectFailure>> termination =
            new CompletableFuture<>();
    private final Consumer<ConnectedLobbySession> ownershipReleased;
    private final Object commandLock = new Object();

    private long nextRequestId = 1L;
    private PendingCommand pendingCommand;
    private PendingRealtimeTicket pendingRealtimeTicket;

    ConnectedLobbySession(
            AuthenticatedReliableSession session,
            LobbySnapshot initialSnapshot,
            LobbyMatchPhaseSnapshot initialMatchSnapshot,
            Consumer<ConnectedLobbySession> ownershipReleased) {
        this.session = Objects.requireNonNull(session, "session");
        playerId = session.playerId();
        handle = session.handle();
        snapshot =
                new AtomicReference<>(Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
        matchSnapshot =
                new AtomicReference<>(
                        Objects.requireNonNull(initialMatchSnapshot, "initialMatchSnapshot"));
        this.ownershipReleased = Objects.requireNonNull(ownershipReleased, "ownershipReleased");
        requireExactSelf(initialSnapshot, playerId, handle);
        requireMatchingInitialSnapshots(initialSnapshot, initialMatchSnapshot);
    }

    public PlayerId playerId() {
        return playerId;
    }

    public CanonicalHandle handle() {
        return handle;
    }

    public LobbySnapshot currentSnapshot() {
        return snapshot.get();
    }

    public LobbyMatchPhaseSnapshot currentMatchSnapshot() {
        return matchSnapshot.get();
    }

    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.assignment());
    }

    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.scene());
    }

    public boolean isOpen() {
        return receiverStarted.get() && !closing.get() && session.isOpen();
    }

    public boolean commandInFlight() {
        synchronized (commandLock) {
            return pendingCommand != null;
        }
    }

    public boolean realtimeTicketRequestInFlight() {
        synchronized (commandLock) {
            return pendingRealtimeTicket != null;
        }
    }

    public Optional<DirectConnectFailure> terminalFailure() {
        return Optional.ofNullable(terminalFailure.get());
    }

    public CompletionStage<Optional<DirectConnectFailure>> termination() {
        return termination.minimalCompletionStage();
    }

    public LobbyCommandSubmission selectTeam(LobbyTeam team) {
        LobbyTeam selectedTeam = Objects.requireNonNull(team, "team");
        if (selectedTeam == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("select-team command requires a concrete team");
        }
        return submitCommand(
                MessageType.LOBBY_SELECT_TEAM,
                requestId ->
                        LobbyProtocolCodec.encodeSelectTeam(
                                new LobbySelectTeamCommand(requestId, selectedTeam)));
    }

    public LobbyCommandSubmission setReady(boolean ready) {
        return submitCommand(
                MessageType.LOBBY_SET_READY,
                requestId ->
                        LobbyProtocolCodec.encodeSetReady(
                                new LobbySetReadyCommand(requestId, ready)));
    }

    public RealtimeTicketSubmission requestRealtimeTicket() {
        return requestRealtimeTicket(1);
    }

    public RealtimeTicketSubmission requestRealtimeTicket(int profileVersion) {
        RealtimeTicketRequest request;
        PendingRealtimeTicket submitted;
        byte[] payload;
        synchronized (commandLock) {
            if (!isOpen()) {
                return RealtimeTicketSubmission.rejected(
                        RealtimeTicketSubmissionStatus.SESSION_CLOSED);
            }
            if (pendingCommand != null || pendingRealtimeTicket != null) {
                return RealtimeTicketSubmission.rejected(
                        RealtimeTicketSubmissionStatus.REQUEST_IN_FLIGHT);
            }
            if (nextRequestId == Long.MAX_VALUE) {
                finish(
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.INTERNAL_FAILURE)));
                return RealtimeTicketSubmission.rejected(
                        RealtimeTicketSubmissionStatus.SESSION_CLOSED);
            }
            long requestId = nextRequestId++;
            request = new RealtimeTicketRequest(requestId, profileVersion);
            payload = RealtimeTicketProtocolCodec.encodeRequest(request);
            submitted = new PendingRealtimeTicket(request);
            pendingRealtimeTicket = submitted;
        }

        try {
            Objects.requireNonNull(
                            session.reliableChannel()
                                    .send(MessageType.REALTIME_TICKET_REQUEST, payload),
                            "realtime ticket request send stage")
                    .whenComplete(
                            (ignored, sendFailure) -> {
                                if (sendFailure != null) {
                                    failOwnedRealtimeTicketRequest(submitted);
                                }
                            });
        } catch (RuntimeException exception) {
            failOwnedRealtimeTicketRequest(submitted);
        }
        return RealtimeTicketSubmission.submitted(submitted.handle);
    }

    public CompletionStage<Void> closeAsync() {
        finish(Optional.empty());
        return closeFuture.minimalCompletionStage();
    }

    @Override
    public void close() {
        closeAsync();
    }

    boolean startReceiving() {
        if (!receiverStarted.compareAndSet(false, true) || closing.get()) {
            return false;
        }
        try {
            Thread.ofVirtual()
                    .name("sunderfront-direct-connect-lobby-receiver")
                    .start(this::receiveLoop);
            return true;
        } catch (RuntimeException exception) {
            finish(Optional.of(DirectConnectFailure.of(DirectConnectFailureCode.INTERNAL_FAILURE)));
            return false;
        }
    }

    static boolean containsExactSelf(
            LobbySnapshot candidate, PlayerId expectedPlayerId, CanonicalHandle expectedHandle) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        Objects.requireNonNull(expectedHandle, "expectedHandle");
        for (LobbyMember member : candidate.members()) {
            if (member.playerId().equals(expectedPlayerId)) {
                return member.handle().equals(expectedHandle);
            }
        }
        return false;
    }

    private LobbyCommandSubmission submitCommand(
            MessageType messageType, LongFunction<byte[]> payloadFactory) {
        PendingCommand submitted;
        byte[] payload;
        synchronized (commandLock) {
            if (!isOpen()) {
                return LobbyCommandSubmission.rejected(LobbyCommandSubmissionStatus.SESSION_CLOSED);
            }
            if (pendingCommand != null || pendingRealtimeTicket != null) {
                return LobbyCommandSubmission.rejected(
                        LobbyCommandSubmissionStatus.COMMAND_IN_FLIGHT);
            }
            if (nextRequestId == Long.MAX_VALUE) {
                finish(
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.INTERNAL_FAILURE)));
                return LobbyCommandSubmission.rejected(LobbyCommandSubmissionStatus.SESSION_CLOSED);
            }
            long requestId = nextRequestId++;
            payload = Objects.requireNonNull(payloadFactory.apply(requestId), "command payload");
            submitted = new PendingCommand(requestId);
            pendingCommand = submitted;
        }

        try {
            Objects.requireNonNull(
                            session.reliableChannel().send(messageType, payload),
                            "lobby command send stage")
                    .whenComplete(
                            (ignored, sendFailure) -> {
                                if (sendFailure != null) {
                                    failOwnedPending(
                                            submitted,
                                            DirectConnectFailureCode.LOBBY_COMMAND_SEND_FAILED);
                                }
                            });
        } catch (RuntimeException exception) {
            failOwnedPending(submitted, DirectConnectFailureCode.LOBBY_COMMAND_SEND_FAILED);
        }
        return LobbyCommandSubmission.submitted(submitted.handle);
    }

    private void receiveLoop() {
        Optional<DirectConnectFailure> failure = Optional.empty();
        try {
            while (!closing.get()) {
                Optional<ProtocolEnvelope> received =
                        Objects.requireNonNull(
                                        session.reliableChannel().receive(), "lobby receive stage")
                                .toCompletableFuture()
                                .get();
                if (received.isEmpty()) {
                    if (!closing.get()) {
                        failure =
                                Optional.of(
                                        DirectConnectFailure.of(
                                                DirectConnectFailureCode.CONNECTION_CLOSED));
                    }
                    break;
                }
                Optional<DirectConnectFailure> messageFailure =
                        processEnvelope(received.orElseThrow());
                if (messageFailure.isPresent()) {
                    failure = messageFailure;
                    break;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!closing.get()) {
                failure =
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.CONNECTION_CLOSED));
            }
        } catch (ExecutionException | RuntimeException exception) {
            if (!closing.get()) {
                failure =
                        Optional.of(
                                DirectConnectFailure.of(
                                        DirectConnectFailureCode.CONNECTION_CLOSED));
            }
        } finally {
            finish(failure);
        }
    }

    private Optional<DirectConnectFailure> processEnvelope(ProtocolEnvelope envelope) {
        return switch (envelope.messageType()) {
            case LOBBY_SNAPSHOT -> processSnapshot(envelope.payload());
            case LOBBY_MATCH_SNAPSHOT -> processMatchSnapshot(envelope.payload());
            case PREPARATION_SPAWN_ASSIGNMENT ->
                    processPreparationSpawnAssignment(envelope.payload());
            case LOBBY_COMMAND_RESULT -> processCommandResult(envelope.payload());
            case REALTIME_TICKET_RESULT -> processRealtimeTicketResult(envelope.payload());
            default ->
                    Optional.of(
                            DirectConnectFailure.of(DirectConnectFailureCode.UNEXPECTED_MESSAGE));
        };
    }

    private Optional<DirectConnectFailure> processSnapshot(byte[] payload) {
        LobbySnapshot next;
        try {
            next = LobbyProtocolCodec.decodeSnapshot(payload);
        } catch (LobbyProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(DirectConnectFailureCode.LOBBY_SNAPSHOT_MALFORMED));
        }
        if (!containsExactSelf(next, playerId, handle)) {
            return Optional.of(
                    DirectConnectFailure.of(DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH));
        }

        PendingCommand completed = null;
        LobbyCommandResolution resolution = null;
        synchronized (commandLock) {
            LobbySnapshot current = snapshot.get();
            if (next.revision() <= current.revision()) {
                return Optional.of(
                        DirectConnectFailure.of(DirectConnectFailureCode.LOBBY_SNAPSHOT_STALE));
            }
            if (pendingCommand != null && pendingCommand.result != null) {
                if (next.revision() != pendingCommand.result.revision()) {
                    return Optional.of(
                            DirectConnectFailure.of(
                                    DirectConnectFailureCode.LOBBY_COMMAND_REVISION_MISMATCH));
                }
                completed = pendingCommand;
                pendingCommand = null;
                resolution = new LobbyCommandResolution.Completed(completed.result, next);
            }
            snapshot.set(next);
        }
        if (completed != null) {
            completed.completion.complete(resolution);
        }
        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processMatchSnapshot(byte[] payload) {
        LobbyMatchPhaseSnapshot next;
        try {
            next = LobbyMatchProtocolCodec.decodeSnapshot(payload);
        } catch (LobbyProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_MALFORMED));
        }

        synchronized (commandLock) {
            LobbyMatchPhaseSnapshot current = matchSnapshot.get();
            if (next.revision() < current.revision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_STALE));
            }
            if (next.revision() == current.revision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_DUPLICATE));
            }
            if (current.revision() == Long.MAX_VALUE
                    || next.revision() != current.revision() + 1L) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_REVISION_GAP));
            }
            LobbySnapshot currentRoster = snapshot.get();
            if (next.rosterRevision() != currentRoster.revision()
                    || next.connectedPlayers() != currentRoster.members().size()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH));
            }
            matchSnapshot.set(next);
        }
        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processPreparationSpawnAssignment(byte[] payload) {
        PreparationSpawnAssignment assignment;
        try {
            assignment = PreparationSpawnProtocolCodec.decodeAssignment(payload);
        } catch (PreparationProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(DirectConnectFailureCode.PREPARATION_SPAWN_MALFORMED));
        }

        synchronized (commandLock) {
            if (preparationState.get() != null) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_DUPLICATE));
            }
            LobbyMatchPhaseSnapshot currentMatch = matchSnapshot.get();
            if (currentMatch.phase() != LobbyMatchPhase.PREPARATION) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_UNEXPECTED_PHASE));
            }
            LobbySnapshot currentRoster = snapshot.get();
            if (assignment.rosterRevision() != currentRoster.revision()
                    || assignment.rosterRevision() != currentMatch.rosterRevision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROSTER_MISMATCH));
            }
            if (assignment.roundNumber() != currentMatch.roundNumber()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_ROUND_MISMATCH));
            }
            Optional<LobbyTeam> selfTeam = selfTeam(currentRoster);
            if (selfTeam.isEmpty() || assignment.team() != selfTeam.orElseThrow()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_SPAWN_TEAM_MISMATCH));
            }
            VerifiedPreparationScene scene;
            try {
                scene = PreparationSceneLoader.loadDefault(assignment);
            } catch (PreparationSceneLoadException exception) {
                return Optional.of(
                        DirectConnectFailure.of(preparationSceneFailureCode(exception.code())));
            }
            preparationState.set(new PreparationState(assignment, scene));
        }
        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processCommandResult(byte[] payload) {
        LobbyCommandResult result;
        try {
            result = LobbyProtocolCodec.decodeCommandResult(payload);
        } catch (LobbyProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.LOBBY_COMMAND_RESULT_MALFORMED));
        }

        PendingCommand completed = null;
        LobbyCommandResolution resolution = null;
        synchronized (commandLock) {
            if (pendingCommand == null
                    || pendingCommand.requestId != result.requestId()
                    || pendingCommand.result != null) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_COMMAND_RESULT_UNEXPECTED));
            }
            LobbySnapshot current = snapshot.get();
            if (result.outcome() == LobbyCommandOutcome.APPLIED) {
                if (current.revision() == Long.MAX_VALUE
                        || result.revision() != current.revision() + 1L) {
                    return Optional.of(
                            DirectConnectFailure.of(
                                    DirectConnectFailureCode.LOBBY_COMMAND_REVISION_MISMATCH));
                }
                pendingCommand.result = result;
                return Optional.empty();
            }
            if (result.revision() != current.revision()) {
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.LOBBY_COMMAND_REVISION_MISMATCH));
            }
            completed = pendingCommand;
            pendingCommand = null;
            resolution = new LobbyCommandResolution.Completed(result, current);
        }
        completed.completion.complete(resolution);
        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processRealtimeTicketResult(byte[] payload) {
        RealtimeTicketResult result;
        try {
            result = RealtimeTicketProtocolCodec.decodeResult(payload);
        } catch (RealtimeTicketProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.REALTIME_TICKET_RESULT_MALFORMED));
        }

        PendingRealtimeTicket completed;
        synchronized (commandLock) {
            if (pendingRealtimeTicket == null
                    || pendingRealtimeTicket.request.requestId() != result.requestId()
                    || pendingRealtimeTicket.request.profileVersion() != result.profileVersion()) {
                result.close();
                return Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.REALTIME_TICKET_RESULT_UNEXPECTED));
            }
            completed = pendingRealtimeTicket;
            pendingRealtimeTicket = null;
        }
        completed.completion.complete(result);
        return Optional.empty();
    }

    private void failOwnedRealtimeTicketRequest(PendingRealtimeTicket expected) {
        DirectConnectFailure failure =
                DirectConnectFailure.of(DirectConnectFailureCode.REALTIME_TICKET_SEND_FAILED);
        synchronized (commandLock) {
            if (pendingRealtimeTicket != expected || closing.get()) {
                return;
            }
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            pendingRealtimeTicket = null;
        }
        terminalFailure.set(failure);
        expected.completion.completeExceptionally(
                new RealtimeTicketRequestException(
                        RealtimeTicketRequestException.Code.SEND_FAILED));
        beginTransportClose(Optional.of(failure));
    }

    private void failOwnedPending(PendingCommand expected, DirectConnectFailureCode failureCode) {
        DirectConnectFailure failure = DirectConnectFailure.of(failureCode);
        synchronized (commandLock) {
            if (pendingCommand != expected || closing.get()) {
                return;
            }
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            pendingCommand = null;
        }
        terminalFailure.set(failure);
        expected.completion.complete(new LobbyCommandResolution.Failed(failure));
        beginTransportClose(Optional.of(failure));
    }

    private void finish(Optional<DirectConnectFailure> failure) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        PendingCommand abandoned;
        PendingRealtimeTicket abandonedRealtimeTicket;
        synchronized (commandLock) {
            abandoned = pendingCommand;
            pendingCommand = null;
            abandonedRealtimeTicket = pendingRealtimeTicket;
            pendingRealtimeTicket = null;
        }
        DirectConnectFailure commandFailure =
                failure.orElseGet(
                        () -> DirectConnectFailure.of(DirectConnectFailureCode.CANCELLED));
        if (abandoned != null) {
            abandoned.completion.complete(new LobbyCommandResolution.Failed(commandFailure));
        }
        if (abandonedRealtimeTicket != null) {
            abandonedRealtimeTicket.completion.completeExceptionally(
                    new RealtimeTicketRequestException(
                            RealtimeTicketRequestException.Code.SESSION_TERMINATED));
        }
        failure.ifPresent(terminalFailure::set);
        beginTransportClose(failure);
    }

    private void beginTransportClose(Optional<DirectConnectFailure> failure) {
        CompletionStage<Void> closeStage;
        try {
            closeStage = Objects.requireNonNull(session.closeAsync(), "session close stage");
        } catch (RuntimeException exception) {
            completeClose(failure, exception);
            return;
        }
        closeStage.whenComplete((ignored, closeFailure) -> completeClose(failure, closeFailure));
    }

    private void completeClose(Optional<DirectConnectFailure> failure, Throwable closeFailure) {
        try {
            ownershipReleased.accept(this);
        } catch (RuntimeException ignored) {
            // Ownership cleanup cannot expose callback details to presentation code.
        }
        if (closeFailure == null) {
            closeFuture.complete(null);
        } else {
            closeFuture.completeExceptionally(
                    new IllegalStateException("connected lobby session close failed"));
        }
        termination.complete(failure);
    }

    private Optional<LobbyTeam> selfTeam(LobbySnapshot currentRoster) {
        for (LobbyMember member : currentRoster.members()) {
            if (member.playerId().equals(playerId) && member.handle().equals(handle)) {
                return Optional.of(member.team());
            }
        }
        return Optional.empty();
    }

    private static void requireExactSelf(
            LobbySnapshot initialSnapshot, PlayerId playerId, CanonicalHandle handle) {
        if (!containsExactSelf(initialSnapshot, playerId, handle)) {
            throw new IllegalArgumentException(
                    "initial lobby snapshot must contain the authenticated player");
        }
    }

    private static void requireMatchingInitialSnapshots(
            LobbySnapshot initialSnapshot, LobbyMatchPhaseSnapshot initialMatchSnapshot) {
        if (initialMatchSnapshot.rosterRevision() != initialSnapshot.revision()
                || initialMatchSnapshot.connectedPlayers() != initialSnapshot.members().size()) {
            throw new IllegalArgumentException(
                    "initial match snapshot must describe the initial lobby roster");
        }
    }

    private static DirectConnectFailureCode preparationSceneFailureCode(
            PreparationSceneLoadException.Code code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case BUNDLE_LOAD_FAILED -> DirectConnectFailureCode.PREPARATION_MAP_UNAVAILABLE;
            case MAP_ID_MISMATCH -> DirectConnectFailureCode.PREPARATION_MAP_ID_MISMATCH;
            case MAP_SHA256_MISMATCH -> DirectConnectFailureCode.PREPARATION_MAP_SHA256_MISMATCH;
            case SCENE_INVALID, COLLISION_INVALID ->
                    DirectConnectFailureCode.PREPARATION_SCENE_INVALID;
            case REGION_MISSING, SPAWN_MISSING, SPAWN_STATE_MISMATCH ->
                    DirectConnectFailureCode.PREPARATION_SPAWN_NOT_IN_MAP;
        };
    }

    private record PreparationState(
            PreparationSpawnAssignment assignment, VerifiedPreparationScene scene) {
        private PreparationState {
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(scene, "scene");
        }
    }

    private static final class PendingCommand {
        private final long requestId;
        private final CompletableFuture<LobbyCommandResolution> completion =
                new CompletableFuture<>();
        private final LobbyCommandHandle handle;
        private LobbyCommandResult result;

        private PendingCommand(long requestId) {
            this.requestId = requestId;
            handle = new LobbyCommandHandle(requestId, completion);
        }
    }

    private static final class PendingRealtimeTicket {
        private final RealtimeTicketRequest request;
        private final CompletableFuture<RealtimeTicketResult> completion =
                new CompletableFuture<>();
        private final RealtimeTicketHandle handle;

        private PendingRealtimeTicket(RealtimeTicketRequest request) {
            this.request = Objects.requireNonNull(request, "request");
            handle = new RealtimeTicketHandle(request.requestId(), completion);
        }
    }
}
