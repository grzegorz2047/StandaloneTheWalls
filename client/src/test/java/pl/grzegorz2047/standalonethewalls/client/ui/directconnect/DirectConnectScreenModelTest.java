package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.ConnectedLobbyModel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class DirectConnectScreenModelTest {
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final LobbyMember MEMBER =
            new LobbyMember(PLAYER_ID, new CanonicalHandle("player_one"), LobbyTeam.RED, false);

    @Test
    void requiresStructuredLobbyExactlyWhileConnected() {
        ConnectedLobbyScreenModel lobby = connectedLobby(LobbyMatchPhase.WAITING_FOR_PLAYERS);
        DirectConnectScreenModel connected =
                model(DirectConnectUiPhase.CONNECTED, Optional.empty(), Optional.of(lobby));

        assertEquals(lobby, connected.connectedLobby().orElseThrow());
        assertTrue(connected.connectedLobby().orElseThrow().controlsEnabled());
        assertThrows(
                IllegalArgumentException.class,
                () -> model(DirectConnectUiPhase.CONNECTED, Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> model(DirectConnectUiPhase.FAILED, Optional.empty(), Optional.of(lobby)));
    }

    @Test
    void everyPostCountdownPhaseLocksTeamAndReadinessControlsWithoutHidingTheLobby() {
        for (LobbyMatchPhase phase :
                List.of(
                        LobbyMatchPhase.PREPARATION,
                        LobbyMatchPhase.WALLS_OPENING,
                        LobbyMatchPhase.OPEN_COMBAT)) {
            ConnectedLobbyScreenModel lobby = connectedLobby(phase);
            DirectConnectScreenModel connected =
                    model(DirectConnectUiPhase.CONNECTED, Optional.empty(), Optional.of(lobby));

            assertEquals(phase, connected.connectedLobby().orElseThrow().match().phase());
            assertFalse(connected.connectedLobby().orElseThrow().controlsEnabled());
            assertTrue(connected.connectedLobby().orElseThrow().lobby().ownMember().isPresent());
        }
    }

    @Test
    void restrictsSensitivePresentationDataToIdentityConfirmation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        model(
                                DirectConnectUiPhase.FAILED,
                                Optional.of("0123-4567-89ab-cdef-0123"),
                                Optional.empty()));
    }

    @Test
    void cancellationMessageIsValidOnlyWhileWaiting() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ConnectedLobbyMatchModel(
                                1L,
                                2L,
                                LobbyMatchPhase.START_COUNTDOWN,
                                20L,
                                "Countdown",
                                Optional.of("Cancelled")));
    }

    private static ConnectedLobbyScreenModel connectedLobby(LobbyMatchPhase phase) {
        LobbySnapshot snapshot = new LobbySnapshot(1L, List.of(MEMBER));
        ConnectedLobbyModel model = ConnectedLobbyModel.from(snapshot, Optional.of(PLAYER_ID));
        long ticksRemaining = phase == LobbyMatchPhase.WAITING_FOR_PLAYERS ? 0L : 20L;
        ConnectedLobbyMatchModel match =
                new ConnectedLobbyMatchModel(
                        1L, 2L, phase, ticksRemaining, "Match status", Optional.empty());
        return new ConnectedLobbyScreenModel(model, match, false, "Ready", "");
    }

    private static DirectConnectScreenModel model(
            DirectConnectUiPhase phase,
            Optional<String> fingerprint,
            Optional<ConnectedLobbyScreenModel> connectedLobby) {
        return new DirectConnectScreenModel(
                phase,
                DirectConnectUiFocus.PRIMARY_ACTION,
                "127.0.0.1:27420",
                "player_one",
                "title",
                "status",
                "detail",
                "primary",
                "secondary",
                true,
                true,
                fingerprint,
                connectedLobby);
    }
}
