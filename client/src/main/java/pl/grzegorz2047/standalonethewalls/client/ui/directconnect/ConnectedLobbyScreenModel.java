package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.ui.lobby.ConnectedLobbyModel;

/** Renderer-independent connected-lobby roster and bounded command presentation state. */
public record ConnectedLobbyScreenModel(
        ConnectedLobbyModel lobby,
        boolean commandInFlight,
        String readyAction,
        String commandStatus) {
    public ConnectedLobbyScreenModel {
        Objects.requireNonNull(lobby, "lobby");
        readyAction = Objects.requireNonNull(readyAction, "readyAction");
        commandStatus = Objects.requireNonNull(commandStatus, "commandStatus");
        if (readyAction.isBlank()) {
            throw new IllegalArgumentException("ready action must not be blank");
        }
    }

    public boolean controlsEnabled() {
        return !commandInFlight;
    }
}
