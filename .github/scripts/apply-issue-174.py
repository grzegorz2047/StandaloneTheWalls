from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def append_once(path: str, marker: str, addition: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if addition.strip() in text:
        return
    count = text.count(marker)
    if count != 1:
        raise RuntimeError(f"expected one marker in {path}, found {count}: {marker!r}")
    file.write_text(text.replace(marker, marker + addition, 1), encoding="utf-8")


# The transition publisher returns the exact successfully delivered spawn plan so the
# coordinator can initialize authoritative movement from the same allocation.
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationTransitionPublisher.java",
    "    public static void publish(\n",
    "    public static List<PreparationClientSpawn> publish(\n",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationTransitionPublisher.java",
    """        } catch (PreparationSpawnPublishException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED,
                    \"preparation spawn assignment publication failed\",
                    exception);
        }
    }
""",
    """        } catch (PreparationSpawnPublishException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED,
                    \"preparation spawn assignment publication failed\",
                    exception);
        }
        return plan;
    }
""",
)

# Allow the existing predicted client state to be reconciled to a validated server snapshot.
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPlayerState.java",
    """    public double pitchDegrees() {
        return pitchDegrees;
    }

    public PreparationPlayerState moveHorizontal(double deltaX, double deltaZ) {
""",
    """    public double pitchDegrees() {
        return pitchDegrees;
    }

    public PreparationPlayerState withAuthoritativeState(
            double x,
            double y,
            double z,
            double authoritativeYawDegrees,
            double authoritativePitchDegrees) {
        return new PreparationPlayerState(
                scene,
                new MapVector3(x, y, z),
                normalizeYaw(authoritativeYawDegrees),
                authoritativePitchDegrees);
    }

    public PreparationPlayerState moveHorizontal(double deltaX, double deltaZ) {
""",
)

# ConnectedLobbySession remains the single receive loop and owns one bounded in-flight input send.
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationMovementProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """    private final AtomicReference<PreparationState> preparationState = new AtomicReference<>();
    private final AtomicReference<DirectConnectFailure> terminalFailure = new AtomicReference<>();
""",
    """    private final AtomicReference<PreparationState> preparationState = new AtomicReference<>();
    private final AtomicReference<PreparationWorldSnapshot> preparationWorldSnapshot =
            new AtomicReference<>();
    private final AtomicReference<DirectConnectFailure> terminalFailure = new AtomicReference<>();
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """    private final AtomicBoolean receiverStarted = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
""",
    """    private final AtomicBoolean receiverStarted = new AtomicBoolean();
    private final AtomicBoolean preparationInputSendInFlight = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.scene());
    }

    public boolean isOpen() {
""",
    """    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        PreparationState current = preparationState.get();
        return current == null ? Optional.empty() : Optional.of(current.scene());
    }

    public Optional<PreparationWorldSnapshot> currentPreparationWorldSnapshot() {
        return Optional.ofNullable(preparationWorldSnapshot.get());
    }

    public boolean isOpen() {
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """        return RealtimeTicketSubmission.submitted(submitted.handle);
    }

    public CompletionStage<Void> closeAsync() {
""",
    """        return RealtimeTicketSubmission.submitted(submitted.handle);
    }

    public boolean submitPreparationInput(PreparationInput input) {
        PreparationInput value = Objects.requireNonNull(input, \"input\");
        PreparationState current = preparationState.get();
        if (!isOpen()
                || current == null
                || current.assignment().roundNumber() != value.roundNumber()
                || !preparationInputSendInFlight.compareAndSet(false, true)) {
            return false;
        }
        byte[] payload = PreparationMovementProtocolCodec.encodeInput(value);
        try {
            Objects.requireNonNull(
                            session.reliableChannel()
                                    .send(MessageType.PREPARATION_INPUT, payload),
                            \"preparation input send stage\")
                    .whenComplete(
                            (ignored, sendFailure) -> {
                                preparationInputSendInFlight.set(false);
                                if (sendFailure != null) {
                                    failPreparationInputSend();
                                }
                            });
            return true;
        } catch (RuntimeException exception) {
            preparationInputSendInFlight.set(false);
            failPreparationInputSend();
            return false;
        }
    }

    public CompletionStage<Void> closeAsync() {
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """            case PREPARATION_SPAWN_ASSIGNMENT ->
                    processPreparationSpawnAssignment(envelope.payload());
            case LOBBY_COMMAND_RESULT -> processCommandResult(envelope.payload());
""",
    """            case PREPARATION_SPAWN_ASSIGNMENT ->
                    processPreparationSpawnAssignment(envelope.payload());
            case PREPARATION_SNAPSHOT -> processPreparationSnapshot(envelope.payload());
            case LOBBY_COMMAND_RESULT -> processCommandResult(envelope.payload());
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processCommandResult(byte[] payload) {
""",
    """        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processPreparationSnapshot(byte[] payload) {
        PreparationWorldSnapshot next;
        try {
            next = PreparationMovementProtocolCodec.decodeSnapshot(payload);
        } catch (PreparationProtocolException exception) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.PREPARATION_SNAPSHOT_MALFORMED));
        }
        PreparationState current = preparationState.get();
        if (current == null || current.assignment().roundNumber() != next.roundNumber()) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.PREPARATION_SNAPSHOT_UNEXPECTED));
        }
        PreparationWorldSnapshot previous = preparationWorldSnapshot.get();
        if (previous != null && next.authoritativeTick() <= previous.authoritativeTick()) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.PREPARATION_SNAPSHOT_STALE));
        }
        boolean containsSelf =
                next.players().stream().anyMatch(player -> player.playerId().equals(playerId));
        if (!containsSelf) {
            return Optional.of(
                    DirectConnectFailure.of(
                            DirectConnectFailureCode.PREPARATION_SNAPSHOT_SELF_MISSING));
        }
        preparationWorldSnapshot.set(next);
        return Optional.empty();
    }

    private Optional<DirectConnectFailure> processCommandResult(byte[] payload) {
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySession.java",
    """    private void failOwnedRealtimeTicketRequest(PendingRealtimeTicket expected) {
""",
    """    private void failPreparationInputSend() {
        finish(
                Optional.of(
                        DirectConnectFailure.of(
                                DirectConnectFailureCode.PREPARATION_INPUT_SEND_FAILED)));
    }

    private void failOwnedRealtimeTicketRequest(PendingRealtimeTicket expected) {
""",
)

# The UI controller delegates movement to the session without owning another transport loop.
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java",
    """import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java",
    """import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java",
    """    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {
            return Optional.empty();
        }
        return session.currentVerifiedPreparationScene();
    }

    @Override
""",
    """    public Optional<VerifiedPreparationScene> currentVerifiedPreparationScene() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {
            return Optional.empty();
        }
        return session.currentVerifiedPreparationScene();
    }

    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        return session == null ? Optional.empty() : session.currentPreparationSpawnAssignment();
    }

    public Optional<PreparationWorldSnapshot> currentPreparationWorldSnapshot() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        return session == null ? Optional.empty() : session.currentPreparationWorldSnapshot();
    }

    public Optional<PlayerId> currentPlayerId() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        return session == null ? Optional.empty() : Optional.of(session.playerId());
    }

    public boolean submitPreparationInput(PreparationInput input) {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        return session != null && session.submitPreparationInput(input);
    }

    @Override
""",
)

# Renderer: retain the existing local prediction controller, send intent at 20 Hz,
# reconcile snapshots, and present remote players using jME primitives.
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;
""",
    """import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationRemotePlayerRenderer;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneGraphException;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private static final String INPUT_MOVE_RIGHT = \"sunderfront-move-right\";

    private static final UiTargetId DIRECT_ENDPOINT_TARGET = new UiTargetId(\"direct.endpoint\");
""",
    """    private static final String INPUT_MOVE_RIGHT = \"sunderfront-move-right\";
    private static final double PREPARATION_INPUT_INTERVAL_SECONDS = 0.05d;

    private static final UiTargetId DIRECT_ENDPOINT_TARGET = new UiTargetId(\"direct.endpoint\");
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private PreparationCollisionWorld preparationCollisionWorld;
    private Node preparationWorld;
    private volatile int renderedWidth = -1;
""",
    """    private PreparationCollisionWorld preparationCollisionWorld;
    private PreparationRemotePlayerRenderer preparationRemotePlayers;
    private Node preparationWorld;
    private PlayerId preparationPlayerId;
    private long preparationRoundNumber;
    private long nextPreparationInputSequence = 1L;
    private long appliedPreparationSnapshotTick = -1L;
    private double preparationInputAccumulator;
    private volatile int renderedWidth = -1;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """        if (screen == Screen.PREPARATION && !smokeMode) {
            updatePreparationMovement(timePerFrame);
        }
""",
    """        if (screen == Screen.PREPARATION && !smokeMode) {
            applyPreparationSnapshot();
            updatePreparationMovement(timePerFrame);
            submitPreparationInput(timePerFrame);
        }
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private void rotatePreparation(double horizontalMousePixels, double verticalMousePixels) {
""",
    """    private void applyPreparationSnapshot() {
        DirectConnectUiController controller = directConnectController;
        PreparationPlayerState current = preparationPlayerState;
        PlayerId localPlayerId = preparationPlayerId;
        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;
        if (controller == null || current == null || localPlayerId == null || remotePlayers == null) {
            failPreparationSceneEntry();
            return;
        }
        Optional<PreparationWorldSnapshot> available = controller.currentPreparationWorldSnapshot();
        if (available.isEmpty()) {
            return;
        }
        PreparationWorldSnapshot snapshot = available.orElseThrow();
        if (snapshot.authoritativeTick() <= appliedPreparationSnapshotTick) {
            return;
        }
        if (snapshot.roundNumber() != preparationRoundNumber) {
            failPreparationSceneEntry();
            return;
        }
        Optional<PreparationPlayerSnapshot> ownSnapshot =
                snapshot.players().stream()
                        .filter(player -> player.playerId().equals(localPlayerId))
                        .findFirst();
        if (ownSnapshot.isEmpty()) {
            failPreparationSceneEntry();
            return;
        }
        PreparationPlayerSnapshot authoritative = ownSnapshot.orElseThrow();
        try {
            preparationPlayerState =
                    current.withAuthoritativeState(
                            authoritative.xMetres(),
                            authoritative.yMetres(),
                            authoritative.zMetres(),
                            authoritative.yawDegrees(),
                            authoritative.pitchDegrees());
            PreparationCameraPlacement.apply(cam, preparationPlayerState);
            remotePlayers.apply(snapshot, localPlayerId);
            appliedPreparationSnapshotTick = snapshot.authoritativeTick();
        } catch (IllegalArgumentException exception) {
            failPreparationSceneEntry();
        }
    }

    private void submitPreparationInput(float timePerFrame) {
        DirectConnectUiController controller = directConnectController;
        PreparationPlayerState current = preparationPlayerState;
        if (controller == null
                || current == null
                || preparationRoundNumber < 1L
                || !Float.isFinite(timePerFrame)) {
            failPreparationSceneEntry();
            return;
        }
        preparationInputAccumulator += Math.max(0.0d, timePerFrame);
        if (preparationInputAccumulator < PREPARATION_INPUT_INTERVAL_SECONDS) {
            return;
        }
        preparationInputAccumulator %= PREPARATION_INPUT_INTERVAL_SECONDS;
        if (nextPreparationInputSequence == Long.MAX_VALUE) {
            failPreparationSceneEntry();
            return;
        }
        PreparationInput input =
                new PreparationInput(
                        preparationRoundNumber,
                        nextPreparationInputSequence,
                        quantizeAxis(preparationInput.forwardAxis()),
                        quantizeAxis(preparationInput.rightAxis()),
                        quantizeYaw(current.yawDegrees()),
                        quantizePitch(current.pitchDegrees()));
        if (controller.submitPreparationInput(input)) {
            nextPreparationInputSequence++;
        }
    }

    private static int quantizeAxis(double value) {
        return (int) Math.round(value * PreparationInput.MAXIMUM_AXIS);
    }

    private static int quantizeYaw(double value) {
        long rounded = Math.round(value * 100.0d);
        return (int) (rounded == 18_000L ? -18_000L : rounded);
    }

    private static int quantizePitch(double value) {
        return (int) Math.round(value * 100.0d);
    }

    private void rotatePreparation(double horizontalMousePixels, double verticalMousePixels) {
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private void enterPreparation(PreparationPlayerState entered) {
        if (screen != Screen.DIRECT_CONNECT) {
            return;
        }
        PreparationPlayerState player = Objects.requireNonNull(entered, \"entered\");
        Node loadedWorld = null;
""",
    """    private void enterPreparation(PreparationPlayerState entered) {
        if (screen != Screen.DIRECT_CONNECT) {
            return;
        }
        PreparationPlayerState player = Objects.requireNonNull(entered, \"entered\");
        DirectConnectUiController controller = directConnectController;
        Optional<PreparationSpawnAssignment> assignment =
                controller == null
                        ? Optional.empty()
                        : controller.currentPreparationSpawnAssignment();
        Optional<PlayerId> localPlayerId =
                controller == null ? Optional.empty() : controller.currentPlayerId();
        if (!smokeMode && (assignment.isEmpty() || localPlayerId.isEmpty())) {
            failPreparationSceneEntry();
            return;
        }
        Node loadedWorld = null;
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """            preparationWorld = loadedWorld;
            preparationCollisionWorld = loadedCollisions;
            rootNode.attachChild(loadedWorld);
            PreparationCameraPlacement.apply(cam, player);
""",
    """            preparationWorld = loadedWorld;
            preparationCollisionWorld = loadedCollisions;
            preparationRoundNumber = assignment.orElseThrow().roundNumber();
            preparationPlayerId = localPlayerId.orElseThrow();
            nextPreparationInputSequence = 1L;
            appliedPreparationSnapshotTick = -1L;
            preparationInputAccumulator = 0.0d;
            preparationRemotePlayers = new PreparationRemotePlayerRenderer(assetManager);
            rootNode.attachChild(loadedWorld);
            rootNode.attachChild(preparationRemotePlayers.root());
            PreparationCameraPlacement.apply(cam, player);
""",
)
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private void detachPreparationWorld() {
        preparationInput.release();
        preparationCollisionWorld = null;
        Node current = preparationWorld;
""",
    """    private void detachPreparationWorld() {
        preparationInput.release();
        preparationCollisionWorld = null;
        preparationPlayerId = null;
        preparationRoundNumber = 0L;
        nextPreparationInputSequence = 1L;
        appliedPreparationSnapshotTick = -1L;
        preparationInputAccumulator = 0.0d;
        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;
        preparationRemotePlayers = null;
        if (remotePlayers != null) {
            remotePlayers.close();
        }
        Node current = preparationWorld;
""",
)

# Server coordinator: receive watcher writes latest-wins mailboxes; the existing fixed tick owns state.
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """import java.util.Arrays;
import java.util.LinkedHashMap;
""",
    """import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolCodec;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationMovementProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolCodec;
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationMapDefinition;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationClientSpawn;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationInputMailbox;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationMapDefinition;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationMovementSimulation;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """    private static final int DEFAULT_TICK_RATE = 20;
    private static final long POLL_MILLIS = 10L;
""",
    """    private static final int DEFAULT_TICK_RATE = 20;
    private static final int PREPARATION_SNAPSHOT_INTERVAL_TICKS = 2;
    private static final long POLL_MILLIS = 10L;
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """                if (changed.isPresent()) {
                    LobbyMatchSnapshot snapshot = changed.orElseThrow();
                    visibleMatchSnapshot.set(snapshot);
                    stabilizeMatchSnapshots(state, snapshot);
                }
                nextTick = Math.addExact(nextTick, 1L);
""",
    """                if (changed.isPresent()) {
                    LobbyMatchSnapshot snapshot = changed.orElseThrow();
                    visibleMatchSnapshot.set(snapshot);
                    stabilizeMatchSnapshots(state, snapshot);
                }
                PreparationMovementSimulation movement = state.preparationMovement;
                if (movement != null && movement.lastAdvancedTick() < nextTick) {
                    advancePreparationMovement(state, movement, nextTick);
                }
                nextTick = Math.addExact(nextTick, 1L);
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """        commitRoster(state, decision.state());
        revokeOwnedRealtimeTicket(member);
        closeSession(member.session);
""",
    """        commitRoster(state, decision.state());
        revokeOwnedRealtimeTicket(member);
        member.preparationInputMailbox.close();
        PreparationMovementSimulation movement = state.preparationMovement;
        if (movement != null) {
            movement.remove(member.identity.playerId());
        }
        closeSession(member.session);
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """    private void publishPreparationTransition(LobbyState state, LobbyMatchSnapshot matchSnapshot) {
        PreparationTransitionPublisher.publish(
                preparationMap,
                state.roster,
                matchSnapshot,
                preparationChannels(state),
                sendTimeout);
    }

    private static Map<LobbyParticipantId, ReliableChannel> preparationChannels(LobbyState state) {
""",
    """    private void publishPreparationTransition(LobbyState state, LobbyMatchSnapshot matchSnapshot) {
        List<PreparationClientSpawn> plan =
                PreparationTransitionPublisher.publish(
                        preparationMap,
                        state.roster,
                        matchSnapshot,
                        preparationChannels(state),
                        sendTimeout);
        Map<pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId,
                        PreparationSpawnAssignment>
                assignments = new HashMap<>();
        for (PreparationClientSpawn delivery : plan) {
            MemberState member = state.members.get(delivery.participantId());
            if (member == null) {
                throw new IllegalStateException(
                        \"preparation transition plan contains an unowned participant\");
            }
            member.preparationInputMailbox.open(matchSnapshot.roundNumber());
            assignments.put(member.identity.playerId(), delivery.assignment());
        }
        state.preparationMovement =
                PreparationMovementSimulation.start(
                        matchSnapshot.roundNumber(),
                        matchSnapshot.authoritativeTick(),
                        preparationMap,
                        assignments);
        publishPreparationSnapshot(state, state.preparationMovement.currentSnapshot().orElseThrow());
    }

    private void advancePreparationMovement(
            LobbyState state, PreparationMovementSimulation movement, long authoritativeTick) {
        Map<pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId, PreparationInput>
                latestInputs = new HashMap<>();
        for (MemberState member : state.members.values()) {
            member.preparationInputMailbox
                    .drainLatest()
                    .ifPresent(input -> latestInputs.put(member.identity.playerId(), input));
        }
        PreparationWorldSnapshot snapshot = movement.advanceTick(authoritativeTick, latestInputs);
        if (authoritativeTick % PREPARATION_SNAPSHOT_INTERVAL_TICKS == 0L) {
            publishPreparationSnapshot(state, snapshot);
        }
    }

    private void publishPreparationSnapshot(
            LobbyState state, PreparationWorldSnapshot snapshot) {
        if (state.members.isEmpty()) {
            return;
        }
        byte[] payload = PreparationMovementProtocolCodec.encodeSnapshot(snapshot);
        List<LobbyParticipantId> failed =
                sendToMembers(state.members, MessageType.PREPARATION_SNAPSHOT, payload);
        if (!failed.isEmpty()) {
            removeFailedMembers(state, failed);
            stabilizeSnapshots(state);
        }
    }

    private static Map<LobbyParticipantId, ReliableChannel> preparationChannels(LobbyState state) {
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """                Command command = decodeClientCommand(member, received.orElseThrow());
                enqueue(command);
            }
        } catch (LobbyProtocolException | RealtimeTicketProtocolException exception) {
""",
    """                ProtocolEnvelope envelope = received.orElseThrow();
                if (envelope.messageType() == MessageType.PREPARATION_INPUT) {
                    acceptPreparationInput(member, envelope.payload());
                    continue;
                }
                Command command = decodeClientCommand(member, envelope);
                enqueue(command);
            }
        } catch (LobbyProtocolException
                | RealtimeTicketProtocolException
                | PreparationProtocolException exception) {
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """    private static Command decodeClientCommand(MemberState member, ProtocolEnvelope envelope)
            throws LobbyProtocolException, RealtimeTicketProtocolException {
""",
    """    private static void acceptPreparationInput(MemberState member, byte[] payload)
            throws PreparationProtocolException {
        PreparationInput input = PreparationMovementProtocolCodec.decodeInput(payload);
        PreparationInputMailbox.OfferResult result = member.preparationInputMailbox.offer(input);
        if (result != PreparationInputMailbox.OfferResult.ACCEPTED) {
            throw new PreparationProtocolException(
                    PreparationProtocolException.Code.INVALID_STATE,
                    \"preparation input is not valid for the owned session state\");
        }
    }

    private static Command decodeClientCommand(MemberState member, ProtocolEnvelope envelope)
            throws LobbyProtocolException, RealtimeTicketProtocolException {
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """            revokeOwnedRealtimeTicket(member);
            sessions.add(member.session);
""",
    """            revokeOwnedRealtimeTicket(member);
            member.preparationInputMailbox.close();
            sessions.add(member.session);
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """        private final AuthorizedPlayerSession session;
        private long lastRequestId;
""",
    """        private final AuthorizedPlayerSession session;
        private final PreparationInputMailbox preparationInputMailbox =
                new PreparationInputMailbox();
        private long lastRequestId;
""",
)
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """        private LobbyRosterState roster = LobbyRosterState.initial();
        private boolean preparationTransitionAttempted;
""",
    """        private LobbyRosterState roster = LobbyRosterState.initial();
        private PreparationMovementSimulation preparationMovement;
        private boolean preparationTransitionAttempted;
""",
)

# Public localized failures remain bounded and do not expose transport details.
append_once(
    "client/src/main/resources/i18n/messages_en.properties",
    "direct.failure.preparation_spawn_not_in_map=The server preparation spawn is not present in the verified local map.\n",
    """direct.failure.preparation_input_send_failed=The preparation movement input could not be sent. The secure connection was closed.
direct.failure.preparation_snapshot_malformed=The server returned an invalid preparation world snapshot.
direct.failure.preparation_snapshot_unexpected=The preparation world snapshot targeted another phase or round.
direct.failure.preparation_snapshot_stale=The preparation world snapshot tick was stale or repeated.
direct.failure.preparation_snapshot_self_missing=The authoritative preparation snapshot did not contain this player.
""",
)
append_once(
    "client/src/main/resources/i18n/messages_pl.properties",
    "direct.failure.preparation_spawn_not_in_map=Spawn przygotowania serwera nie występuje w zweryfikowanej mapie lokalnej.\n",
    """direct.failure.preparation_input_send_failed=Nie udało się wysłać ruchu przygotowania. Bezpieczne połączenie zostało zamknięte.
direct.failure.preparation_snapshot_malformed=Serwer zwrócił nieprawidłowy snapshot świata przygotowania.
direct.failure.preparation_snapshot_unexpected=Snapshot świata przygotowania dotyczył innej fazy albo rundy.
direct.failure.preparation_snapshot_stale=Tick snapshotu świata przygotowania był nieaktualny albo powtórzony.
direct.failure.preparation_snapshot_self_missing=Autorytatywny snapshot przygotowania nie zawierał tego gracza.
""",
)

# Expose the simulation tick for coordinator catch-up without allowing mutation.
replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java",
    """    public int playerCount() {
        return players.size();
    }

    public boolean remove(PlayerId playerId) {
""",
    """    public int playerCount() {
        return players.size();
    }

    public long lastAdvancedTick() {
        return lastAdvancedTick;
    }

    public boolean remove(PlayerId playerId) {
""",
)
