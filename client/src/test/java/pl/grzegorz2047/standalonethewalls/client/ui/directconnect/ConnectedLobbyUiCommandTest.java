package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectProgressListener;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectUiTestFixtures;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectUiTestFixtures.ControlledLobby;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectUiTestFixtures.SentCommand;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class ConnectedLobbyUiCommandTest {
    private static final ClientMessages MESSAGES =
            ClientMessages.forLanguage(ClientLanguage.ENGLISH);
    private static final List<LobbyCommandOutcome> REJECTIONS =
            List.of(
                    LobbyCommandOutcome.LOBBY_FULL,
                    LobbyCommandOutcome.DUPLICATE_PARTICIPANT,
                    LobbyCommandOutcome.UNKNOWN_PARTICIPANT,
                    LobbyCommandOutcome.TEAM_DISABLED,
                    LobbyCommandOutcome.TEAM_FULL,
                    LobbyCommandOutcome.TEAM_IMBALANCE,
                    LobbyCommandOutcome.TEAM_REQUIRED);

    @Test
    void submitsExactCommandsAndPublishesOnlyAuthoritativeResolutions()
            throws LobbyProtocolException, InterruptedException {
        ControlledLobby lobby = DirectConnectUiTestFixtures.controlledLobby();
        DirectConnectUiController controller = connectedController(lobby);

        assertEquals(DirectConnectUiFocus.TEAM_RED, controller.model().focus());
        assertTrue(controller.focus(DirectConnectUiFocus.TEAM_GREEN));
        controller.activate();

        assertBusy(controller);
        SentCommand select = onlyCommand(lobby, 1);
        assertEquals(MessageType.LOBBY_SELECT_TEAM, select.messageType());
        assertEquals(
                new LobbySelectTeamCommand(1L, LobbyTeam.GREEN),
                LobbyProtocolCodec.decodeSelectTeam(select.payload()));

        assertTrue(controller.focus(DirectConnectUiFocus.READY_ACTION));
        controller.activate();
        assertEquals(1, lobby.sentCommands().size());
        assertEquals(
                MESSAGES.text("direct.lobby.command.busy"), connected(controller).commandStatus());

        lobby.deliverResult(new LobbyCommandResult(1L, 2L, LobbyCommandOutcome.APPLIED), 1L);
        TimeUnit.MILLISECONDS.sleep(20L);
        assertBusy(controller);
        assertEquals(
                LobbyTeam.UNASSIGNED,
                connected(controller).lobby().ownMember().orElseThrow().team());

        lobby.deliverSnapshot(
                DirectConnectUiTestFixtures.snapshot(
                        2L, LobbyTeam.GREEN, false, LobbyTeam.BLUE, false),
                2L);
        waitUntil(
                () ->
                        connected(controller).lobby().revision() == 2L
                                && connected(controller).controlsEnabled());
        assertEquals(
                LobbyTeam.GREEN, connected(controller).lobby().ownMember().orElseThrow().team());
        assertEquals(
                MESSAGES.text("direct.lobby.command.applied"),
                connected(controller).commandStatus());

        controller.focus(DirectConnectUiFocus.READY_ACTION);
        controller.activate();
        SentCommand ready = onlyCommand(lobby, 2);
        assertEquals(MessageType.LOBBY_SET_READY, ready.messageType());
        assertEquals(
                new LobbySetReadyCommand(2L, true),
                LobbyProtocolCodec.decodeSetReady(ready.payload()));
        lobby.deliverResult(new LobbyCommandResult(2L, 3L, LobbyCommandOutcome.APPLIED), 3L);
        lobby.deliverSnapshot(
                DirectConnectUiTestFixtures.snapshot(
                        3L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, false),
                4L);
        waitUntil(
                () ->
                        connected(controller).lobby().revision() == 3L
                                && connected(controller).controlsEnabled());
        assertTrue(connected(controller).lobby().ownMember().orElseThrow().ready());
        assertEquals(
                MESSAGES.text("direct.lobby.action.not_ready"),
                connected(controller).readyAction());

        controller.focus(DirectConnectUiFocus.TEAM_GREEN);
        controller.activate();
        onlyCommand(lobby, 3);
        lobby.deliverResult(new LobbyCommandResult(3L, 3L, LobbyCommandOutcome.NO_CHANGE), 5L);
        waitUntil(() -> connected(controller).controlsEnabled());
        assertEquals(
                MESSAGES.text("direct.lobby.command.no_change"),
                connected(controller).commandStatus());

        lobby.deliverSnapshot(
                DirectConnectUiTestFixtures.snapshot(
                        4L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true),
                6L);
        waitUntil(
                () -> {
                    controller.refreshConnectedSnapshot();
                    return connected(controller).lobby().revision() == 4L;
                });
        assertEquals(4L, connected(controller).lobby().revision());
        assertTrue(
                connected(controller).lobby().panel(LobbyTeam.BLUE).members().getFirst().ready());

        long requestId = 4L;
        long sequence = 7L;
        for (LobbyCommandOutcome rejection : REJECTIONS) {
            controller.focus(DirectConnectUiFocus.TEAM_RED);
            controller.activate();
            onlyCommand(lobby, (int) requestId);
            lobby.deliverResult(new LobbyCommandResult(requestId, 4L, rejection), sequence++);
            waitUntil(() -> connected(controller).controlsEnabled());
            assertEquals(
                    MESSAGES.text(
                            "direct.lobby.command." + rejection.name().toLowerCase(Locale.ROOT)),
                    connected(controller).commandStatus());
            assertEquals(DirectConnectUiPhase.CONNECTED, controller.model().phase());
            requestId++;
        }

        controller.focus(DirectConnectUiFocus.TEAM_YELLOW);
        controller.activate();
        assertBusy(controller);
        controller.escape();
        waitUntil(() -> controller.model().phase() == DirectConnectUiPhase.DISCONNECTED);
        assertTrue(controller.model().connectedLobby().isEmpty());
        controller.close();
    }

    @Test
    void eofDuringBusyClearsControlsAndProducesBoundedDisconnectedState()
            throws InterruptedException {
        ControlledLobby lobby = DirectConnectUiTestFixtures.controlledLobby();
        DirectConnectUiController controller = connectedController(lobby);

        controller.focus(DirectConnectUiFocus.TEAM_BLUE);
        controller.activate();
        assertBusy(controller);
        lobby.deliverEof();

        waitUntil(() -> controller.model().phase() == DirectConnectUiPhase.DISCONNECTED);
        assertTrue(controller.model().connectedLobby().isEmpty());
        assertFalse(controller.model().detail().isBlank());
        assertFalse(controller.model().detail().contains("Exception"));
        controller.close();
    }

    @Test
    void keyboardFocusTraversesTeamsReadyAndLifecycleActions() {
        ControlledLobby lobby = DirectConnectUiTestFixtures.controlledLobby();
        DirectConnectUiController controller = connectedController(lobby);

        List<DirectConnectUiFocus> expected =
                List.of(
                        DirectConnectUiFocus.TEAM_BLUE,
                        DirectConnectUiFocus.TEAM_GREEN,
                        DirectConnectUiFocus.TEAM_YELLOW,
                        DirectConnectUiFocus.READY_ACTION,
                        DirectConnectUiFocus.PRIMARY_ACTION,
                        DirectConnectUiFocus.SECONDARY_ACTION,
                        DirectConnectUiFocus.TEAM_RED);
        for (DirectConnectUiFocus focus : expected) {
            controller.moveFocus(1);
            assertEquals(focus, controller.model().focus());
        }
        controller.close();
    }

    private static DirectConnectUiController connectedController(ControlledLobby lobby) {
        DirectConnectUiController controller =
                new DirectConnectUiController(
                        new ImmediateConnectedBackend(lobby),
                        MESSAGES,
                        Runnable::run,
                        ignored -> {},
                        () -> {});
        controller.open();
        controller.focus(DirectConnectUiFocus.PRIMARY_ACTION);
        controller.activate();
        assertEquals(DirectConnectUiPhase.CONNECTED, controller.model().phase());
        return controller;
    }

    private static ConnectedLobbyScreenModel connected(DirectConnectUiController controller) {
        return controller.model().connectedLobby().orElseThrow();
    }

    private static SentCommand onlyCommand(ControlledLobby lobby, int expectedCount) {
        assertEquals(expectedCount, lobby.sentCommands().size());
        return lobby.sentCommands().get(expectedCount - 1);
    }

    private static void assertBusy(DirectConnectUiController controller) {
        assertTrue(connected(controller).commandInFlight());
        assertFalse(connected(controller).controlsEnabled());
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

    private static final class ImmediateConnectedBackend implements DirectConnectUiBackend {
        private final DirectConnectResult result;

        private ImmediateConnectedBackend(ControlledLobby lobby) {
            result = lobby.connectedResult(PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED);
        }

        @Override
        public DirectConnectUiAttempt connect(
                DirectConnectEndpoint endpoint,
                CanonicalHandle handle,
                DirectConnectProgressListener progressListener) {
            return new DirectConnectUiAttempt() {
                @Override
                public CompletionStage<DirectConnectResult> result() {
                    return CompletableFuture.completedFuture(result);
                }

                @Override
                public boolean cancel() {
                    return false;
                }
            };
        }

        @Override
        public DirectConnectUiAttempt confirmFirstUse(
                FirstUseConfirmation confirmation, DirectConnectProgressListener progressListener) {
            throw new AssertionError("first-use confirmation is outside this test");
        }

        @Override
        public void discardPendingConfirmation() {}

        @Override
        public void close() {}
    }
}
