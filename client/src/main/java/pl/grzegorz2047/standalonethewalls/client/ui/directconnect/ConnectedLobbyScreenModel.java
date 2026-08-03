package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.ConnectedLobbyModel;

/** Renderer-independent connected-lobby roster, match phase, and command presentation state. */
public record ConnectedLobbyScreenModel(
        ConnectedLobbyModel lobby,
        ConnectedLobbyMatchModel match,
        boolean commandInFlight,
        String readyAction,
        String commandStatus) {
    public ConnectedLobbyScreenModel {
        Objects.requireNonNull(lobby, "lobby");
        Objects.requireNonNull(match, "match");
        readyAction = Objects.requireNonNull(readyAction, "readyAction");
        commandStatus = Objects.requireNonNull(commandStatus, "commandStatus");
        if (readyAction.isBlank()) {
            throw new IllegalArgumentException("ready action must not be blank");
        }
    }

    public boolean controlsEnabled() {
        return !commandInFlight && match.lobbyControlsAllowed();
    }

    public boolean ownReady() {
        return lobby.ownMember().orElseThrow().ready();
    }
}
