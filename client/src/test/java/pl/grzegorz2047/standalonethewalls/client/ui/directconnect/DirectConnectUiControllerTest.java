package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.network.ConnectedLobbySession;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpointException;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectFailure;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectFailureCode;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectProgressListener;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectStage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectUiTestFixtures;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class DirectConnectUiControllerTest {
    private static final ClientMessages MESSAGES =
            ClientMessages.forLanguage(ClientLanguage.ENGLISH);

    @Test
    void validatesEndpointAndHandleBeforeStartingNetworkWork() {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();

        for (int index = 0; index < DirectConnectUiController.DEFAULT_ENDPOINT.length(); index++) {
            controller.backspace();
        }
        selectPrimaryAction(controller);
        controller.activate();

        assertEquals(DirectConnectUiPhase.FORM, controller.model().phase());
        assertEquals(DirectConnectUiFocus.ENDPOINT, controller.model().focus());
        assertTrue(backend.connectAttempts.isEmpty());

        for (char character : DirectConnectUiController.DEFAULT_ENDPOINT.toCharArray()) {
            controller.appendCharacter(character);
        }
        controller.moveFocus(1);
        for (int index = 0; index < "player_one".length(); index++) {
            controller.backspace();
        }
        controller.moveFocus(1);
        controller.activate();

        assertEquals(DirectConnectUiPhase.FORM, controller.model().phase());
        assertEquals(DirectConnectUiFocus.HANDLE, controller.model().focus());
        assertTrue(backend.connectAttempts.isEmpty());
        controller.close();
    }

    @Test
    void completesExplicitFirstUseFlowAndOwnsConnectedLobby()
            throws DirectConnectEndpointException, InterruptedException {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        ControllableAttempt first = backend.connectAttempts.getFirst();

        for (DirectConnectStage stage : DirectConnectStage.values()) {
            first.emit(stage);
            assertEquals(expectedPhase(stage), controller.model().phase());
        }

        FirstUseConfirmation confirmation = DirectConnectUiTestFixtures.confirmation();
        first.complete(new DirectConnectResult.ConfirmationRequired(confirmation));
        assertEquals(DirectConnectUiPhase.CONFIRMING_IDENTITY, controller.model().phase());
        assertEquals(
                confirmation.fingerprint().value(), controller.model().fingerprint().orElseThrow());

        controller.activate();
        ControllableAttempt confirmed = backend.confirmAttempts.getFirst();
        confirmed.emit(DirectConnectStage.AUTHENTICATING);
        assertEquals(DirectConnectUiPhase.AUTHENTICATING, controller.model().phase());

        ConnectedLobbySession session = DirectConnectUiTestFixtures.openLobbySession();
        confirmed.complete(
                new DirectConnectResult.Connected(
                        session, PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED));

        assertEquals(DirectConnectUiPhase.CONNECTED, controller.model().phase());
        ConnectedLobbyScreenModel connected = controller.model().connectedLobby().orElseThrow();
        assertEquals(2, connected.lobby().totalMembers());
        assertEquals(LobbyTeam.UNASSIGNED, connected.lobby().ownMember().orElseThrow().team());
        assertTrue(connected.controlsEnabled());
        assertFalse(connected.readyAction().isBlank());
        assertEquals(DirectConnectUiFocus.TEAM_RED, controller.model().focus());
        assertEquals("player_one", controller.model().handleText());

        controller.escape();
        assertEquals(DirectConnectUiPhase.DISCONNECTED, controller.model().phase());
        waitUntil(() -> !session.isOpen());
        controller.close();
    }

    @Test
    void exposesTheVerifiedPreparationSceneAfterTheAuthoritativeAssignment()
            throws DirectConnectEndpointException, InterruptedException {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        backend.connectAttempts
                .getFirst()
                .complete(
                        lobby.connectedResult(
                                PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED));

        assertTrue(controller.currentVerifiedPreparationScene().isEmpty());
        LobbySnapshot roster =
                DirectConnectUiTestFixtures.snapshot(
                        2L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true);
        lobby.deliverSnapshot(roster, 2L);
        lobby.deliverMatchSnapshot(
                new LobbyMatchPhaseSnapshot(
                        2L,
                        roster.revision(),
                        10L,
                        LobbyMatchPhase.PREPARATION,
                        100L,
                        roster.members().size(),
                        1L,
                        LobbyCountdownCancellationReason.NONE),
                3L);
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        lobby.deliverPreparationSpawnAssignment(
                new PreparationSpawnAssignment(
                        roster.revision(),
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d),
                4L);

        waitUntil(() -> controller.currentVerifiedPreparationScene().isPresent());
        assertEquals(
                MinimalPreparationBundle.MAP_ID,
                controller.currentVerifiedPreparationScene().orElseThrow().mapId());
        assertEquals(0, controller.currentVerifiedPreparationScene().orElseThrow().spawn().index());
        controller.close();
    }

    @Test
    void escapeFromFirstUseConfirmationDiscardsTrustWithoutReconnect()
            throws DirectConnectEndpointException {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        backend.connectAttempts
                .getFirst()
                .complete(
                        new DirectConnectResult.ConfirmationRequired(
                                DirectConnectUiTestFixtures.confirmation()));

        controller.escape();

        assertEquals(DirectConnectUiPhase.FORM, controller.model().phase());
        assertEquals(1, backend.discardCount.get());
        assertTrue(backend.confirmAttempts.isEmpty());
        controller.close();
    }

    @Test
    void distinguishesSecurityAlertAdmissionRejectionAndTimeout() {
        assertFailurePhase(
                DirectConnectFailure.of(DirectConnectFailureCode.CHANGED_SERVER_IDENTITY),
                DirectConnectUiPhase.SECURITY_ALERT);
        assertFailurePhase(
                DirectConnectFailure.admissionRejected(PlayerSessionAdmissionStatus.PLAYER_BANNED),
                DirectConnectUiPhase.ADMISSION_REJECTED);
        assertFailurePhase(
                DirectConnectFailure.of(DirectConnectFailureCode.ADMISSION_TIMEOUT),
                DirectConnectUiPhase.FAILED);
    }

    @Test
    void staleCallbacksAfterCancelCannotOverwriteANewerAttempt() throws InterruptedException {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        ControllableAttempt first = backend.connectAttempts.getFirst();

        controller.escape();
        assertEquals(DirectConnectUiPhase.FORM, controller.model().phase());
        waitUntil(first.cancelled::get);

        startConnection(controller);
        ControllableAttempt second = backend.connectAttempts.get(1);
        assertEquals(DirectConnectUiPhase.RESOLVING, controller.model().phase());

        first.emit(DirectConnectStage.JOINING_LOBBY);
        first.complete(
                new DirectConnectResult.Failed(
                        DirectConnectFailure.of(DirectConnectFailureCode.INTERNAL_FAILURE)));
        assertEquals(DirectConnectUiPhase.RESOLVING, controller.model().phase());

        second.complete(
                new DirectConnectResult.Failed(
                        DirectConnectFailure.of(DirectConnectFailureCode.DNS_OR_CONNECT_FAILED)));
        assertEquals(DirectConnectUiPhase.FAILED, controller.model().phase());
        controller.close();
    }

    private static DirectConnectUiController controller(FakeBackend backend) {
        return new DirectConnectUiController(
                backend, MESSAGES, Runnable::run, ignored -> {}, () -> {});
    }

    private static void startConnection(DirectConnectUiController controller) {
        selectPrimaryAction(controller);
        controller.activate();
        assertEquals(DirectConnectUiPhase.RESOLVING, controller.model().phase());
    }

    private static void selectPrimaryAction(DirectConnectUiController controller) {
        controller.moveFocus(1);
        controller.moveFocus(1);
        assertEquals(DirectConnectUiFocus.PRIMARY_ACTION, controller.model().focus());
    }

    private static void assertFailurePhase(
            DirectConnectFailure failure, DirectConnectUiPhase expectedPhase) {
        FakeBackend backend = new FakeBackend();
        DirectConnectUiController controller = controller(backend);
        controller.open();
        startConnection(controller);
        backend.connectAttempts.getFirst().complete(new DirectConnectResult.Failed(failure));

        assertEquals(expectedPhase, controller.model().phase());
        assertFalse(controller.model().detail().isBlank());
        assertTrue(controller.model().fingerprint().isEmpty());
        assertTrue(controller.model().connectedLobby().isEmpty());
        controller.close();
    }

    private static DirectConnectUiPhase expectedPhase(DirectConnectStage stage) {
        return switch (stage) {
            case RESOLVING -> DirectConnectUiPhase.RESOLVING;
            case CONNECTING -> DirectConnectUiPhase.CONNECTING;
            case SECURING_TRANSPORT -> DirectConnectUiPhase.SECURING_TRANSPORT;
            case AUTHENTICATING -> DirectConnectUiPhase.AUTHENTICATING;
            case WAITING_ADMISSION -> DirectConnectUiPhase.WAITING_ADMISSION;
            case JOINING_LOBBY -> DirectConnectUiPhase.JOINING_LOBBY;
        };
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
    }

    private static final class FakeBackend implements DirectConnectUiBackend {
        private final List<ControllableAttempt> connectAttempts = new ArrayList<>();
        private final List<ControllableAttempt> confirmAttempts = new ArrayList<>();
        private final AtomicInteger discardCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public DirectConnectUiAttempt connect(
                DirectConnectEndpoint endpoint,
                CanonicalHandle handle,
                DirectConnectProgressListener progressListener) {
            ControllableAttempt attempt = new ControllableAttempt(progressListener);
            connectAttempts.add(attempt);
            return attempt;
        }

        @Override
        public DirectConnectUiAttempt confirmFirstUse(
                FirstUseConfirmation confirmation, DirectConnectProgressListener progressListener) {
            ControllableAttempt attempt = new ControllableAttempt(progressListener);
            confirmAttempts.add(attempt);
            return attempt;
        }

        @Override
        public void discardPendingConfirmation() {
            discardCount.incrementAndGet();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class ControllableAttempt implements DirectConnectUiAttempt {
        private final DirectConnectProgressListener progressListener;
        private final CompletableFuture<DirectConnectResult> result = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ControllableAttempt(DirectConnectProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public CompletionStage<DirectConnectResult> result() {
            return result.minimalCompletionStage();
        }

        @Override
        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            result.complete(
                    new DirectConnectResult.Failed(
                            DirectConnectFailure.of(DirectConnectFailureCode.CANCELLED)));
            return true;
        }

        private void emit(DirectConnectStage stage) {
            progressListener.onStage(stage);
        }

        private void complete(DirectConnectResult terminalResult) {
            result.complete(terminalResult);
        }
    }
}
