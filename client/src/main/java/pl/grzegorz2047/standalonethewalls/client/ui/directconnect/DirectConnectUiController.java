package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.network.ConnectedLobbySession;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectAttempt;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpointException;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectFailure;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectFailureCode;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectProgressListener;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectStage;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.client.network.LobbyCommandHandle;
import pl.grzegorz2047.standalonethewalls.client.network.LobbyCommandResolution;
import pl.grzegorz2047.standalonethewalls.client.network.LobbyCommandSubmission;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.ConnectedLobbyModel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Renderer-owned state machine around the asynchronous Direct Connect service. */
public final class DirectConnectUiController implements AutoCloseable {
    public static final String DEFAULT_ENDPOINT = "127.0.0.1:27420";
    private static final int MAXIMUM_HANDLE_LENGTH = 24;

    private final DirectConnectUiBackend backend;
    private final ClientMessages messages;
    private final UiDispatcher dispatcher;
    private final Consumer<DirectConnectScreenModel> observer;
    private final Runnable exitToMenu;
    private final AtomicBoolean closed = new AtomicBoolean();

    private String endpointText = DEFAULT_ENDPOINT;
    private String handleText = "player_one";
    private DirectConnectUiFocus focus = DirectConnectUiFocus.ENDPOINT;
    private DirectConnectScreenModel model;
    private DirectConnectUiAttempt activeAttempt;
    private FirstUseConfirmation confirmation;
    private ConnectedLobbySession connectedSession;
    private LobbyCommandHandle pendingLobbyCommand;
    private String lobbyCommandStatus;
    private final AtomicLong connectedRevision = new AtomicLong(-1L);
    private final AtomicLong generation = new AtomicLong();

    public DirectConnectUiController(
            DirectConnectService service,
            ClientMessages messages,
            UiDispatcher dispatcher,
            Consumer<DirectConnectScreenModel> observer,
            Runnable exitToMenu) {
        this(new ServiceBackend(service), messages, dispatcher, observer, exitToMenu);
    }

    DirectConnectUiController(
            DirectConnectUiBackend backend,
            ClientMessages messages,
            UiDispatcher dispatcher,
            Consumer<DirectConnectScreenModel> observer,
            Runnable exitToMenu) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.dispatcher = UiDispatcher.require(dispatcher);
        this.observer = Objects.requireNonNull(observer, "observer");
        this.exitToMenu = Objects.requireNonNull(exitToMenu, "exitToMenu");
        lobbyCommandStatus = messages.text("direct.lobby.command.idle");
        model = formModel(messages.text("direct.status.ready"));
    }

    public DirectConnectScreenModel model() {
        return model;
    }

    public void open() {
        requireOpen();
        publish(formModel(messages.text("direct.status.ready")));
    }

    public void appendCharacter(char character) {
        requireOpen();
        if (!model.editingEnabled() || Character.isISOControl(character)) {
            return;
        }
        switch (focus) {
            case ENDPOINT -> {
                if (endpointText.length() < DirectConnectEndpoint.MAXIMUM_INPUT_CHARACTERS) {
                    endpointText += character;
                }
            }
            case HANDLE -> {
                char canonical = Character.toLowerCase(character);
                if (handleText.length() < MAXIMUM_HANDLE_LENGTH
                        && ((canonical >= 'a' && canonical <= 'z')
                                || (canonical >= '0' && canonical <= '9')
                                || canonical == '_')) {
                    handleText += canonical;
                }
            }
            case TEAM_RED,
                    TEAM_BLUE,
                    TEAM_GREEN,
                    TEAM_YELLOW,
                    READY_ACTION,
                    PRIMARY_ACTION,
                    SECONDARY_ACTION -> {
                return;
            }
        }
        publish(formModel(messages.text("direct.status.ready")));
    }

    public void backspace() {
        requireOpen();
        if (!model.editingEnabled()) {
            return;
        }
        switch (focus) {
            case ENDPOINT -> endpointText = removeLast(endpointText);
            case HANDLE -> handleText = removeLast(handleText);
            case TEAM_RED,
                    TEAM_BLUE,
                    TEAM_GREEN,
                    TEAM_YELLOW,
                    READY_ACTION,
                    PRIMARY_ACTION,
                    SECONDARY_ACTION -> {
                return;
            }
        }
        publish(formModel(messages.text("direct.status.ready")));
    }

    public void moveFocus(int direction) {
        requireOpen();
        if (direction == 0) {
            return;
        }
        List<DirectConnectUiFocus> allowed = allowedFocuses(model.phase());
        int current = Math.max(0, allowed.indexOf(focus));
        focus = allowed.get(Math.floorMod(current + Integer.signum(direction), allowed.size()));
        publish(copyWithFocus(model, focus));
    }

    public boolean focus(DirectConnectUiFocus target) {
        requireOpen();
        Objects.requireNonNull(target, "target");
        if (!allowedFocuses(model.phase()).contains(target)) {
            return false;
        }
        if (focus == target) {
            return true;
        }
        focus = target;
        publish(copyWithFocus(model, focus));
        return true;
    }

    public void activate() {
        requireOpen();
        switch (model.phase()) {
            case FORM -> activateForm();
            case RESOLVING,
                    CONNECTING,
                    SECURING_TRANSPORT,
                    AUTHENTICATING,
                    WAITING_ADMISSION,
                    JOINING_LOBBY ->
                    cancelAttempt();
            case CONFIRMING_IDENTITY -> {
                if (focus == DirectConnectUiFocus.PRIMARY_ACTION) {
                    confirmIdentity();
                } else {
                    cancelConfirmation();
                }
            }
            case CONNECTED -> activateConnected();
            case SECURITY_ALERT, ADMISSION_REJECTED, FAILED, DISCONNECTED -> {
                if (focus == DirectConnectUiFocus.PRIMARY_ACTION) {
                    focus = DirectConnectUiFocus.ENDPOINT;
                    publish(formModel(messages.text("direct.status.ready")));
                } else {
                    exitToMenu.run();
                }
            }
        }
    }

    public void escape() {
        requireOpen();
        switch (model.phase()) {
            case FORM -> exitToMenu.run();
            case RESOLVING,
                    CONNECTING,
                    SECURING_TRANSPORT,
                    AUTHENTICATING,
                    WAITING_ADMISSION,
                    JOINING_LOBBY ->
                    cancelAttempt();
            case CONFIRMING_IDENTITY -> cancelConfirmation();
            case CONNECTED -> disconnect();
            case SECURITY_ALERT, ADMISSION_REJECTED, FAILED, DISCONNECTED -> {
                focus = DirectConnectUiFocus.ENDPOINT;
                publish(formModel(messages.text("direct.status.ready")));
            }
        }
    }

    /** Called from the renderer update loop to publish newer immutable lobby snapshots. */
    public void refreshConnectedSnapshot() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {
            return;
        }
        LobbySnapshot snapshot = session.currentSnapshot();
        boolean uiPending = pendingLobbyCommand != null;
        boolean sessionPending = session.commandInFlight();
        boolean busy = uiPending || sessionPending;
        ConnectedLobbyScreenModel current = model.connectedLobby().orElseThrow();
        boolean busyChanged = current.commandInFlight() != busy;
        if (snapshot.revision() <= connectedRevision.get() && !busyChanged) {
            return;
        }
        if (uiPending && !sessionPending) {
            return;
        }
        if (snapshot.revision() > connectedRevision.get()) {
            connectedRevision.set(snapshot.revision());
        }
        publish(connectedModel(snapshot, busy, lobbyCommandStatus));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        generation.incrementAndGet();
        DirectConnectUiAttempt attempt = activeAttempt;
        ConnectedLobbySession session = connectedSession;
        activeAttempt = null;
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        confirmation = null;
        backend.discardPendingConfirmation();
        runLifecycle(
                "close",
                () -> {
                    if (attempt != null) {
                        attempt.cancel();
                    }
                    awaitSessionClose(session);
                    backend.close();
                });
    }

    private void activateForm() {
        switch (focus) {
            case ENDPOINT -> {
                focus = DirectConnectUiFocus.HANDLE;
                publish(copyWithFocus(model, focus));
            }
            case HANDLE -> {
                focus = DirectConnectUiFocus.PRIMARY_ACTION;
                publish(copyWithFocus(model, focus));
            }
            case PRIMARY_ACTION -> beginConnect();
            case SECONDARY_ACTION -> exitToMenu.run();
            case TEAM_RED, TEAM_BLUE, TEAM_GREEN, TEAM_YELLOW, READY_ACTION -> {
                return;
            }
        }
    }

    private void activateConnected() {
        ConnectedLobbyScreenModel connected = model.connectedLobby().orElseThrow();
        switch (focus) {
            case TEAM_RED -> submitTeam(LobbyTeam.RED, connected);
            case TEAM_BLUE -> submitTeam(LobbyTeam.BLUE, connected);
            case TEAM_GREEN -> submitTeam(LobbyTeam.GREEN, connected);
            case TEAM_YELLOW -> submitTeam(LobbyTeam.YELLOW, connected);
            case READY_ACTION -> submitReady(connected);
            case PRIMARY_ACTION -> disconnect();
            case SECONDARY_ACTION -> disconnectAndExit();
            case ENDPOINT, HANDLE -> {
                return;
            }
        }
    }

    private void submitTeam(LobbyTeam team, ConnectedLobbyScreenModel connected) {
        ConnectedLobbySession session = connectedSession;
        if (session == null) {
            transitionClosedLobby();
            return;
        }
        if (!connected.controlsEnabled()) {
            publishBusyStatus(session);
            return;
        }
        submitLobbyCommand(session, session.selectTeam(team));
    }

    private void submitReady(ConnectedLobbyScreenModel connected) {
        ConnectedLobbySession session = connectedSession;
        if (session == null) {
            transitionClosedLobby();
            return;
        }
        if (!connected.controlsEnabled()) {
            publishBusyStatus(session);
            return;
        }
        boolean currentlyReady = connected.lobby().ownMember().orElseThrow().ready();
        submitLobbyCommand(session, session.setReady(!currentlyReady));
    }

    private void submitLobbyCommand(
            ConnectedLobbySession session, LobbyCommandSubmission submission) {
        switch (submission.status()) {
            case SUBMITTED -> {
                LobbyCommandHandle handle = submission.handle().orElseThrow();
                pendingLobbyCommand = handle;
                lobbyCommandStatus = messages.text("direct.lobby.command.submitting");
                publish(connectedModel(session.currentSnapshot(), true, lobbyCommandStatus));
                long commandGeneration = generation.get();
                handle.completion()
                        .whenComplete(
                                (resolution, failure) ->
                                        dispatcher.dispatch(
                                                () ->
                                                        handleLobbyCommandCompletion(
                                                                session,
                                                                commandGeneration,
                                                                handle.requestId(),
                                                                resolution,
                                                                failure)));
            }
            case COMMAND_IN_FLIGHT -> publishBusyStatus(session);
            case SESSION_CLOSED -> transitionClosedLobby();
        }
    }

    private void publishBusyStatus(ConnectedLobbySession session) {
        lobbyCommandStatus = messages.text("direct.lobby.command.busy");
        publish(connectedModel(session.currentSnapshot(), true, lobbyCommandStatus));
    }

    private void handleLobbyCommandCompletion(
            ConnectedLobbySession expectedSession,
            long commandGeneration,
            long requestId,
            LobbyCommandResolution resolution,
            Throwable completionFailure) {
        LobbyCommandHandle pending = pendingLobbyCommand;
        if (!isCurrent(commandGeneration)
                || connectedSession != expectedSession
                || pending == null
                || pending.requestId() != requestId) {
            return;
        }
        pendingLobbyCommand = null;
        if (completionFailure != null || resolution == null) {
            transitionTerminalLobbyFailure(
                    expectedSession,
                    DirectConnectFailure.of(DirectConnectFailureCode.INTERNAL_FAILURE));
            return;
        }
        switch (resolution) {
            case LobbyCommandResolution.Completed completed -> {
                LobbySnapshot snapshot = completed.snapshot();
                connectedRevision.set(snapshot.revision());
                lobbyCommandStatus = commandOutcomeMessage(completed.result().outcome());
                publish(connectedModel(snapshot, false, lobbyCommandStatus));
            }
            case LobbyCommandResolution.Failed failed ->
                    transitionTerminalLobbyFailure(expectedSession, failed.failure());
        }
    }

    private void transitionTerminalLobbyFailure(
            ConnectedLobbySession expectedSession, DirectConnectFailure failure) {
        generation.incrementAndGet();
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        focus = DirectConnectUiFocus.PRIMARY_ACTION;
        publish(disconnectedModel(failureMessage(failure)));
        runLifecycle("lobby-command-failure", () -> awaitSessionClose(expectedSession));
    }

    private void transitionClosedLobby() {
        generation.incrementAndGet();
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        focus = DirectConnectUiFocus.PRIMARY_ACTION;
        publish(disconnectedModel(messages.text("direct.lobby.command.session_closed")));
    }

    private void beginConnect() {
        DirectConnectEndpoint endpoint;
        CanonicalHandle handle;
        try {
            endpoint = DirectConnectEndpoint.parse(endpointText);
        } catch (DirectConnectEndpointException exception) {
            focus = DirectConnectUiFocus.ENDPOINT;
            publish(formModel(messages.text("direct.validation.endpoint")));
            return;
        }
        try {
            handle = new CanonicalHandle(handleText);
        } catch (IllegalArgumentException exception) {
            focus = DirectConnectUiFocus.HANDLE;
            publish(formModel(messages.text("direct.validation.handle")));
            return;
        }

        long attemptGeneration = generation.incrementAndGet();
        confirmation = null;
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        lobbyCommandStatus = messages.text("direct.lobby.command.idle");
        focus = DirectConnectUiFocus.SECONDARY_ACTION;
        publish(progressModel(DirectConnectUiPhase.RESOLVING));
        activeAttempt =
                backend.connect(
                        endpoint,
                        handle,
                        stage ->
                                dispatcher.dispatch(
                                        () -> handleProgress(attemptGeneration, stage)));
        activeAttempt
                .result()
                .whenComplete(
                        (result, failure) ->
                                dispatcher.dispatch(
                                        () -> handleResult(attemptGeneration, result, failure)));
    }

    private void confirmIdentity() {
        if (confirmation == null) {
            publish(failureModel(messages.text("direct.failure.confirmation_invalid")));
            return;
        }
        long attemptGeneration = generation.incrementAndGet();
        FirstUseConfirmation accepted = confirmation;
        confirmation = null;
        focus = DirectConnectUiFocus.SECONDARY_ACTION;
        publish(progressModel(DirectConnectUiPhase.RESOLVING));
        activeAttempt =
                backend.confirmFirstUse(
                        accepted,
                        stage ->
                                dispatcher.dispatch(
                                        () -> handleProgress(attemptGeneration, stage)));
        activeAttempt
                .result()
                .whenComplete(
                        (result, failure) ->
                                dispatcher.dispatch(
                                        () -> handleResult(attemptGeneration, result, failure)));
    }

    private void handleProgress(long attemptGeneration, DirectConnectStage stage) {
        if (!isCurrent(attemptGeneration)) {
            return;
        }
        publish(progressModel(mapStage(stage)));
    }

    private void handleResult(
            long attemptGeneration, DirectConnectResult result, Throwable failure) {
        if (!isCurrent(attemptGeneration)) {
            closeStaleConnectedResult(result);
            return;
        }
        activeAttempt = null;
        if (failure != null || result == null) {
            focus = DirectConnectUiFocus.PRIMARY_ACTION;
            publish(failureModel(messages.text("direct.failure.internal_failure")));
            return;
        }
        switch (result) {
            case DirectConnectResult.ConfirmationRequired required -> {
                confirmation = required.confirmation();
                focus = DirectConnectUiFocus.PRIMARY_ACTION;
                publish(confirmationModel(required.confirmation()));
            }
            case DirectConnectResult.Connected connected -> {
                ConnectedLobbySession transferred = connected.takeSession();
                connectedSession = transferred;
                pendingLobbyCommand = null;
                LobbySnapshot snapshot = transferred.currentSnapshot();
                connectedRevision.set(snapshot.revision());
                lobbyCommandStatus = messages.text("direct.lobby.command.idle");
                ConnectedLobbyModel lobby =
                        ConnectedLobbyModel.from(snapshot, Optional.of(transferred.playerId()));
                focus = focusForTeam(lobby.ownMember().orElseThrow().team());
                publish(connectedModel(snapshot, false, lobbyCommandStatus));
                transferred
                        .termination()
                        .whenComplete(
                                (terminalFailure, terminationError) ->
                                        dispatcher.dispatch(
                                                () ->
                                                        handleTermination(
                                                                attemptGeneration,
                                                                transferred,
                                                                terminalFailure,
                                                                terminationError)));
            }
            case DirectConnectResult.Failed failed -> handleFailure(failed.failure());
        }
    }

    private void handleFailure(DirectConnectFailure failure) {
        if (failure.code() == DirectConnectFailureCode.ADMISSION_REJECTED) {
            PlayerSessionAdmissionStatus status = failure.admissionStatus().orElseThrow();
            focus = DirectConnectUiFocus.PRIMARY_ACTION;
            publish(admissionRejectedModel(admissionMessage(status)));
            return;
        }
        focus = DirectConnectUiFocus.PRIMARY_ACTION;
        if (failure.code() == DirectConnectFailureCode.CHANGED_SERVER_IDENTITY
                || failure.code() == DirectConnectFailureCode.EXPECTED_SERVER_IDENTITY_MISMATCH) {
            publish(securityAlertModel(failureMessage(failure)));
        } else {
            publish(failureModel(failureMessage(failure)));
        }
    }

    private void handleTermination(
            long attemptGeneration,
            ConnectedLobbySession expectedSession,
            Optional<DirectConnectFailure> terminalFailure,
            Throwable terminationError) {
        if (!isCurrent(attemptGeneration) || connectedSession != expectedSession) {
            return;
        }
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        focus = DirectConnectUiFocus.PRIMARY_ACTION;
        String detail =
                terminationError != null
                        ? messages.text("direct.failure.connection_closed")
                        : terminalFailure
                                .map(this::failureMessage)
                                .orElse(messages.text("direct.status.disconnected"));
        publish(disconnectedModel(detail));
    }

    private void cancelAttempt() {
        generation.incrementAndGet();
        DirectConnectUiAttempt attempt = activeAttempt;
        activeAttempt = null;
        focus = DirectConnectUiFocus.ENDPOINT;
        publish(formModel(messages.text("direct.status.cancelled")));
        if (attempt != null) {
            runLifecycle("cancel", attempt::cancel);
        }
    }

    private void cancelConfirmation() {
        generation.incrementAndGet();
        backend.discardPendingConfirmation();
        confirmation = null;
        focus = DirectConnectUiFocus.ENDPOINT;
        publish(formModel(messages.text("direct.status.confirmation_cancelled")));
    }

    private void disconnect() {
        generation.incrementAndGet();
        ConnectedLobbySession session = connectedSession;
        connectedSession = null;
        pendingLobbyCommand = null;
        connectedRevision.set(-1L);
        focus = DirectConnectUiFocus.PRIMARY_ACTION;
        publish(disconnectedModel(messages.text("direct.status.disconnected")));
        if (session != null) {
            runLifecycle("disconnect", () -> awaitSessionClose(session));
        }
    }

    private void disconnectAndExit() {
        disconnect();
        exitToMenu.run();
    }

    private boolean isCurrent(long attemptGeneration) {
        return !closed.get() && generation.get() == attemptGeneration;
    }

    private void closeStaleConnectedResult(DirectConnectResult result) {
        if (result instanceof DirectConnectResult.Connected connected) {
            runLifecycle("stale-session", () -> awaitSessionClose(connected.takeSession()));
        }
    }

    private DirectConnectScreenModel formModel(String status) {
        return new DirectConnectScreenModel(
                DirectConnectUiPhase.FORM,
                focus,
                endpointText,
                handleText,
                messages.text("direct.title"),
                status,
                messages.text("direct.detail.form"),
                messages.text("direct.action.connect"),
                messages.text("direct.action.back"),
                true,
                true,
                Optional.empty(),
                Optional.empty());
    }

    private DirectConnectScreenModel progressModel(DirectConnectUiPhase phase) {
        return new DirectConnectScreenModel(
                phase,
                DirectConnectUiFocus.SECONDARY_ACTION,
                endpointText,
                handleText,
                messages.text("direct.title"),
                messages.text(statusKey(phase)),
                messages.text("direct.detail.progress", endpointText),
                messages.text("direct.action.wait"),
                messages.text("direct.action.cancel"),
                false,
                true,
                Optional.empty(),
                Optional.empty());
    }

    private DirectConnectScreenModel confirmationModel(FirstUseConfirmation required) {
        return new DirectConnectScreenModel(
                DirectConnectUiPhase.CONFIRMING_IDENTITY,
                focus,
                endpointText,
                handleText,
                messages.text("direct.confirm.title"),
                messages.text("direct.status.confirming_identity"),
                messages.text("direct.confirm.detail", required.endpoint().authority()),
                messages.text("direct.action.trust"),
                messages.text("direct.action.cancel"),
                true,
                true,
                Optional.of(required.fingerprint().value()),
                Optional.empty());
    }

    private DirectConnectScreenModel connectedModel(
            LobbySnapshot snapshot, boolean commandInFlight, String commandStatus) {
        ConnectedLobbySession session =
                Objects.requireNonNull(connectedSession, "connected session");
        ConnectedLobbyModel lobby =
                ConnectedLobbyModel.from(snapshot, Optional.of(session.playerId()));
        boolean ready = lobby.ownMember().orElseThrow().ready();
        ConnectedLobbyScreenModel connected =
                new ConnectedLobbyScreenModel(
                        lobby,
                        commandInFlight,
                        messages.text(
                                ready
                                        ? "direct.lobby.action.not_ready"
                                        : "direct.lobby.action.ready"),
                        commandStatus);
        return new DirectConnectScreenModel(
                DirectConnectUiPhase.CONNECTED,
                focus,
                endpointText,
                handleText,
                messages.text("direct.lobby.title"),
                messages.text("direct.status.connected"),
                messages.text("direct.lobby.members", lobby.totalMembers()),
                messages.text("direct.action.disconnect"),
                messages.text("direct.action.menu"),
                true,
                true,
                Optional.empty(),
                Optional.of(connected));
    }

    private DirectConnectScreenModel securityAlertModel(String detail) {
        return terminalModel(
                DirectConnectUiPhase.SECURITY_ALERT,
                messages.text("direct.security.title"),
                detail);
    }

    private DirectConnectScreenModel admissionRejectedModel(String detail) {
        return terminalModel(
                DirectConnectUiPhase.ADMISSION_REJECTED, messages.text("direct.title"), detail);
    }

    private DirectConnectScreenModel failureModel(String detail) {
        return terminalModel(DirectConnectUiPhase.FAILED, messages.text("direct.title"), detail);
    }

    private DirectConnectScreenModel disconnectedModel(String detail) {
        return terminalModel(
                DirectConnectUiPhase.DISCONNECTED, messages.text("direct.title"), detail);
    }

    private DirectConnectScreenModel terminalModel(
            DirectConnectUiPhase phase, String title, String detail) {
        return new DirectConnectScreenModel(
                phase,
                focus,
                endpointText,
                handleText,
                title,
                messages.text(statusKey(phase)),
                detail,
                messages.text("direct.action.retry"),
                messages.text("direct.action.menu"),
                true,
                true,
                Optional.empty(),
                Optional.empty());
    }

    private String failureMessage(DirectConnectFailure failure) {
        String key = "direct.failure." + failure.code().name().toLowerCase(Locale.ROOT);
        return messages.text(key);
    }

    private String admissionMessage(PlayerSessionAdmissionStatus status) {
        String key = "direct.admission." + status.name().toLowerCase(Locale.ROOT);
        return messages.text(key);
    }

    private String commandOutcomeMessage(LobbyCommandOutcome outcome) {
        String key = "direct.lobby.command." + outcome.name().toLowerCase(Locale.ROOT);
        return messages.text(key);
    }

    private static DirectConnectUiPhase mapStage(DirectConnectStage stage) {
        return switch (Objects.requireNonNull(stage, "stage")) {
            case RESOLVING -> DirectConnectUiPhase.RESOLVING;
            case CONNECTING -> DirectConnectUiPhase.CONNECTING;
            case SECURING_TRANSPORT -> DirectConnectUiPhase.SECURING_TRANSPORT;
            case AUTHENTICATING -> DirectConnectUiPhase.AUTHENTICATING;
            case WAITING_ADMISSION -> DirectConnectUiPhase.WAITING_ADMISSION;
            case JOINING_LOBBY -> DirectConnectUiPhase.JOINING_LOBBY;
        };
    }

    private static DirectConnectUiFocus focusForTeam(LobbyTeam team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case RED -> DirectConnectUiFocus.TEAM_RED;
            case BLUE -> DirectConnectUiFocus.TEAM_BLUE;
            case GREEN -> DirectConnectUiFocus.TEAM_GREEN;
            case YELLOW -> DirectConnectUiFocus.TEAM_YELLOW;
            case UNASSIGNED -> DirectConnectUiFocus.TEAM_RED;
        };
    }

    private static String statusKey(DirectConnectUiPhase phase) {
        return "direct.status." + phase.name().toLowerCase(Locale.ROOT);
    }

    private static List<DirectConnectUiFocus> allowedFocuses(DirectConnectUiPhase phase) {
        return switch (phase) {
            case FORM ->
                    List.of(
                            DirectConnectUiFocus.ENDPOINT,
                            DirectConnectUiFocus.HANDLE,
                            DirectConnectUiFocus.PRIMARY_ACTION,
                            DirectConnectUiFocus.SECONDARY_ACTION);
            case RESOLVING,
                    CONNECTING,
                    SECURING_TRANSPORT,
                    AUTHENTICATING,
                    WAITING_ADMISSION,
                    JOINING_LOBBY ->
                    List.of(DirectConnectUiFocus.SECONDARY_ACTION);
            case CONNECTED ->
                    List.of(
                            DirectConnectUiFocus.TEAM_RED,
                            DirectConnectUiFocus.TEAM_BLUE,
                            DirectConnectUiFocus.TEAM_GREEN,
                            DirectConnectUiFocus.TEAM_YELLOW,
                            DirectConnectUiFocus.READY_ACTION,
                            DirectConnectUiFocus.PRIMARY_ACTION,
                            DirectConnectUiFocus.SECONDARY_ACTION);
            case CONFIRMING_IDENTITY,
                    SECURITY_ALERT,
                    ADMISSION_REJECTED,
                    FAILED,
                    DISCONNECTED ->
                    List.of(
                            DirectConnectUiFocus.PRIMARY_ACTION,
                            DirectConnectUiFocus.SECONDARY_ACTION);
        };
    }

    private void publish(DirectConnectScreenModel next) {
        model = Objects.requireNonNull(next, "next");
        observer.accept(next);
    }

    private static DirectConnectScreenModel copyWithFocus(
            DirectConnectScreenModel source, DirectConnectUiFocus nextFocus) {
        return new DirectConnectScreenModel(
                source.phase(),
                nextFocus,
                source.endpointText(),
                source.handleText(),
                source.title(),
                source.status(),
                source.detail(),
                source.primaryAction(),
                source.secondaryAction(),
                source.primaryEnabled(),
                source.secondaryEnabled(),
                source.fingerprint(),
                source.connectedLobby());
    }

    private static String removeLast(String value) {
        return value.isEmpty() ? value : value.substring(0, value.length() - 1);
    }

    private static void runLifecycle(String operation, Runnable action) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        Thread.ofVirtual()
                .name("sunderfront-direct-connect-ui-" + operation)
                .start(
                        () -> {
                            try {
                                action.run();
                            } catch (RuntimeException ignored) {
                                // The immutable UI state already records the public outcome.
                            }
                        });
    }

    private static void awaitSessionClose(ConnectedLobbySession session) {
        if (session == null) {
            return;
        }
        try {
            session.closeAsync().toCompletableFuture().join();
        } catch (RuntimeException ignored) {
            // Session cleanup cannot replace the stable immutable UI state.
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Direct Connect UI controller is closed");
        }
    }

    private static final class ServiceBackend implements DirectConnectUiBackend {
        private final DirectConnectService service;

        private ServiceBackend(DirectConnectService service) {
            this.service = Objects.requireNonNull(service, "service");
        }

        @Override
        public DirectConnectUiAttempt connect(
                DirectConnectEndpoint endpoint,
                CanonicalHandle handle,
                DirectConnectProgressListener progressListener) {
            return adapt(service.connect(endpoint, handle, progressListener));
        }

        @Override
        public DirectConnectUiAttempt confirmFirstUse(
                FirstUseConfirmation confirmation, DirectConnectProgressListener progressListener) {
            return adapt(service.confirmFirstUse(confirmation, progressListener));
        }

        @Override
        public void discardPendingConfirmation() {
            service.discardPendingConfirmation();
        }

        @Override
        public void close() {
            service.close();
        }

        private static DirectConnectUiAttempt adapt(DirectConnectAttempt attempt) {
            Objects.requireNonNull(attempt, "attempt");
            return new DirectConnectUiAttempt() {
                @Override
                public java.util.concurrent.CompletionStage<DirectConnectResult> result() {
                    return attempt.result();
                }

                @Override
                public boolean cancel() {
                    return attempt.cancel();
                }
            };
        }
    }
}
