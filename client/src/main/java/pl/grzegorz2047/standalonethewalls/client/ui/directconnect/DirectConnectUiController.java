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
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;

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
            case PRIMARY_ACTION, SECONDARY_ACTION -> {
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
            case PRIMARY_ACTION, SECONDARY_ACTION -> {
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
            case CONNECTED -> {
                if (focus == DirectConnectUiFocus.PRIMARY_ACTION) {
                    disconnect();
                } else {
                    disconnectAndExit();
                }
            }
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
        if (connectedSession == null || model.phase() != DirectConnectUiPhase.CONNECTED) {
            return;
        }
        LobbySnapshot snapshot = connectedSession.currentSnapshot();
        if (snapshot.revision() > connectedRevision.get()) {
            connectedRevision.set(snapshot.revision());
            publish(connectedModel(snapshot));
        }
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
        }
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
        connectedRevision.set(-1L);
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
                connectedSession = connected.session();
                LobbySnapshot snapshot = connected.session().currentSnapshot();
                connectedRevision.set(snapshot.revision());
                focus = DirectConnectUiFocus.PRIMARY_ACTION;
                publish(connectedModel(snapshot));
                connected
                        .session()
                        .termination()
                        .whenComplete(
                                (terminalFailure, terminationError) ->
                                        dispatcher.dispatch(
                                                () ->
                                                        handleTermination(
                                                                attemptGeneration,
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
            Optional<DirectConnectFailure> terminalFailure,
            Throwable terminationError) {
        if (!isCurrent(attemptGeneration)) {
            return;
        }
        connectedSession = null;
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
            runLifecycle("stale-session", () -> awaitSessionClose(connected.session()));
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
                List.of());
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
                List.of());
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
                List.of());
    }

    private DirectConnectScreenModel connectedModel(LobbySnapshot snapshot) {
        return new DirectConnectScreenModel(
                DirectConnectUiPhase.CONNECTED,
                focus,
                endpointText,
                handleText,
                messages.text("direct.lobby.title"),
                messages.text("direct.status.connected"),
                messages.text("direct.lobby.members", snapshot.members().size()),
                messages.text("direct.action.disconnect"),
                messages.text("direct.action.menu"),
                true,
                true,
                Optional.empty(),
                snapshot.members());
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
                List.of());
    }

    private String failureMessage(DirectConnectFailure failure) {
        String key = "direct.failure." + failure.code().name().toLowerCase(Locale.ROOT);
        return messages.text(key);
    }

    private String admissionMessage(PlayerSessionAdmissionStatus status) {
        String key = "direct.admission." + status.name().toLowerCase(Locale.ROOT);
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

    private static String statusKey(DirectConnectUiPhase phase) {
        return "direct.status." + phase.name().toLowerCase(Locale.ROOT);
    }

    private static List<DirectConnectUiFocus> allowedFocuses(DirectConnectUiPhase phase) {
        return switch (phase) {
            case FORM -> List.of(DirectConnectUiFocus.values());
            case RESOLVING,
                    CONNECTING,
                    SECURING_TRANSPORT,
                    AUTHENTICATING,
                    WAITING_ADMISSION,
                    JOINING_LOBBY ->
                    List.of(DirectConnectUiFocus.SECONDARY_ACTION);
            case CONFIRMING_IDENTITY,
                    SECURITY_ALERT,
                    ADMISSION_REJECTED,
                    FAILED,
                    CONNECTED,
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
                source.members());
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
