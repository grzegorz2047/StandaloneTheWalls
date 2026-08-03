package pl.grzegorz2047.standalonethewalls.client.ui.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class ConnectedLobbyModelTest {
    private static final String NATURAL_PLAYER_ID_ALPHABET = "234567abcdefghijklmnopqrstuvwxyz";

    @Test
    void representsAnEmptyRosterWithoutInventingSelfOrCapacity() {
        ConnectedLobbyModel model =
                ConnectedLobbyModel.from(new LobbySnapshot(1L, List.of()), Optional.empty(), 720f);

        assertEquals(0, model.totalMembers());
        assertTrue(model.ownMember().isEmpty());
        assertTrue(model.unassignedMembers().isEmpty());
        assertEquals(LobbyPanelLayout.TWO_BY_TWO, model.layout());
        assertEquals(ConnectedLobbyModel.DISPLAY_TEAM_ORDER,
                model.teamPanels().stream().map(LobbyTeamPanelModel::team).toList());
        assertTrue(model.teamPanels().stream().allMatch(panel -> panel.occupiedSlots() == 0));
    }

    @Test
    void marksOneUnassignedSelfByPlayerIdRatherThanHandle() {
        PlayerId self = playerId(0);
        LobbySnapshot snapshot =
                new LobbySnapshot(
                        2L,
                        List.of(
                                new LobbyMember(
                                        self,
                                        new CanonicalHandle("same_handle"),
                                        LobbyTeam.UNASSIGNED,
                                        false)));

        ConnectedLobbyModel model =
                ConnectedLobbyModel.from(snapshot, Optional.of(self), 1080f);

        assertEquals(LobbyPanelLayout.FOUR_COLUMNS, model.layout());
        assertEquals(1, model.totalMembers());
        LobbyMemberRowModel own = model.ownMember().orElseThrow();
        assertEquals(self, own.playerId());
        assertTrue(own.ownPlayer());
        assertEquals(LobbyTeam.UNASSIGNED, own.team());
        assertFalse(own.ready());
        assertEquals(List.of(own), model.unassignedMembers());
    }

    @Test
    void groupsTenParticipantsAcrossEveryTeamAndUnassigned() {
        List<LobbyMember> members = new ArrayList<>();
        LobbyTeam[] teams = {
            LobbyTeam.UNASSIGNED,
            LobbyTeam.RED,
            LobbyTeam.BLUE,
            LobbyTeam.GREEN,
            LobbyTeam.YELLOW
        };
        for (int index = 0; index < 10; index++) {
            LobbyTeam team = teams[index % teams.length];
            members.add(member(index, team, team != LobbyTeam.UNASSIGNED && index % 2 == 0));
        }
        PlayerId self = playerId(7);

        ConnectedLobbyModel model =
                ConnectedLobbyModel.from(
                        new LobbySnapshot(3L, members), Optional.of(self), 1080f);

        assertEquals(10, model.totalMembers());
        assertEquals(2, model.unassignedMembers().size());
        assertEquals(2, model.panel(LobbyTeam.RED).occupiedSlots());
        assertEquals(2, model.panel(LobbyTeam.BLUE).occupiedSlots());
        assertEquals(2, model.panel(LobbyTeam.GREEN).occupiedSlots());
        assertEquals(2, model.panel(LobbyTeam.YELLOW).occupiedSlots());
        assertEquals(self, model.ownMember().orElseThrow().playerId());
        assertEquals(LobbyTeam.BLUE, model.ownMember().orElseThrow().team());
        assertFalse(model.ownMember().orElseThrow().ready());
    }

    @Test
    void acceptsTheProtocolMaximumOfFortyParticipants() {
        List<LobbyMember> members = new ArrayList<>();
        LobbyTeam[] teams = {
            LobbyTeam.RED, LobbyTeam.BLUE, LobbyTeam.GREEN, LobbyTeam.YELLOW
        };
        for (int index = 0; index < LobbySnapshot.MAXIMUM_MEMBERS; index++) {
            members.add(member(index, teams[index % teams.length], index % 3 == 0));
        }

        ConnectedLobbyModel model =
                ConnectedLobbyModel.from(
                        new LobbySnapshot(9L, members), Optional.of(playerId(39)), 1920f);

        assertEquals(40, model.totalMembers());
        assertTrue(model.unassignedMembers().isEmpty());
        for (LobbyTeam team : ConnectedLobbyModel.DISPLAY_TEAM_ORDER) {
            assertEquals(10, model.panel(team).occupiedSlots());
        }
        assertEquals(playerId(39), model.ownMember().orElseThrow().playerId());
    }

    @Test
    void rejectsMissingSelfAndInvalidViewportWidths() {
        LobbySnapshot snapshot = new LobbySnapshot(1L, List.of(member(0, LobbyTeam.RED, false)));

        assertThrows(
                IllegalArgumentException.class,
                () -> ConnectedLobbyModel.from(snapshot, Optional.of(playerId(1)), 1080f));
        assertThrows(
                IllegalArgumentException.class,
                () -> ConnectedLobbyModel.from(snapshot, Optional.empty(), Float.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> ConnectedLobbyModel.from(snapshot, Optional.empty(), 0f));
    }

    private static LobbyMember member(int index, LobbyTeam team, boolean ready) {
        return new LobbyMember(
                playerId(index), new CanonicalHandle("player_" + index), team, ready);
    }

    private static PlayerId playerId(int index) {
        int base = NATURAL_PLAYER_ID_ALPHABET.length();
        if (index < 0 || index >= base * base) {
            throw new IllegalArgumentException("test player index is outside the supported range");
        }
        char first = NATURAL_PLAYER_ID_ALPHABET.charAt(index / base);
        char second = NATURAL_PLAYER_ID_ALPHABET.charAt(index % base);
        return new PlayerId("sf1_" + first + second + "a".repeat(50));
    }
}
