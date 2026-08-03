package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.ConnectedLobbyModel;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class DirectConnectScreenModelTest {
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final LobbyMember MEMBER =
            new LobbyMember(PLAYER_ID, new CanonicalHandle("player_one"), LobbyTeam.RED, false);

    @Test
    void requiresStructuredLobbyExactlyWhileConnected() {
        ConnectedLobbyScreenModel lobby = connectedLobby();
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
    void restrictsSensitivePresentationDataToIdentityConfirmation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        model(
                                DirectConnectUiPhase.FAILED,
                                Optional.of("0123-4567-89ab-cdef-0123"),
                                Optional.empty()));
    }

    private static ConnectedLobbyScreenModel connectedLobby() {
        LobbySnapshot snapshot = new LobbySnapshot(1L, List.of(MEMBER));
        ConnectedLobbyModel model = ConnectedLobbyModel.from(snapshot, Optional.of(PLAYER_ID));
        return new ConnectedLobbyScreenModel(model, false, "Ready", "");
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
