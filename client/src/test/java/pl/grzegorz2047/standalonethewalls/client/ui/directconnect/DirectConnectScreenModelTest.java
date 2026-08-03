package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;

class DirectConnectScreenModelTest {
    private static final LobbyMember MEMBER =
            new LobbyMember(
                    new PlayerId("sf1_" + "a".repeat(52)), new CanonicalHandle("player_one"));

    @Test
    void defensivelyCopiesLobbyMembersAndRestrictsSensitivePresentationData() {
        List<LobbyMember> mutableMembers = new ArrayList<>(List.of(MEMBER));
        DirectConnectScreenModel connected =
                model(DirectConnectUiPhase.CONNECTED, Optional.empty(), mutableMembers);

        mutableMembers.clear();
        assertEquals(List.of(MEMBER), connected.members());
        assertThrows(UnsupportedOperationException.class, () -> connected.members().clear());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        model(
                                DirectConnectUiPhase.FAILED,
                                Optional.of("0123-4567-89ab-cdef-0123"),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> model(DirectConnectUiPhase.FAILED, Optional.empty(), List.of(MEMBER)));
    }

    private static DirectConnectScreenModel model(
            DirectConnectUiPhase phase, Optional<String> fingerprint, List<LobbyMember> members) {
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
                members);
    }
}
