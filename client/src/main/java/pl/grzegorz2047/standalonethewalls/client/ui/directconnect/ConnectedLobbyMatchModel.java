package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;

/** Renderer-independent presentation of one authoritative match phase snapshot. */
public record ConnectedLobbyMatchModel(
        long revision,
        long authoritativeTick,
        LobbyMatchPhase phase,
        long ticksRemaining,
        String status,
        Optional<String> cancellationMessage) {
    public ConnectedLobbyMatchModel {
        if (revision < 0L) {
            throw new IllegalArgumentException("match revision cannot be negative");
        }
        if (authoritativeTick < -1L) {
            throw new IllegalArgumentException("authoritative tick is outside the supported range");
        }
        Objects.requireNonNull(phase, "phase");
        if (ticksRemaining < 0L) {
            throw new IllegalArgumentException("ticksRemaining cannot be negative");
        }
        status = Objects.requireNonNull(status, "status");
        if (status.isBlank()) {
            throw new IllegalArgumentException("match status must not be blank");
        }
        cancellationMessage =
                Objects.requireNonNull(cancellationMessage, "cancellationMessage")
                        .map(String::strip)
                        .filter(message -> !message.isEmpty());
        if (phase != LobbyMatchPhase.WAITING_FOR_PLAYERS && cancellationMessage.isPresent()) {
            throw new IllegalArgumentException(
                    "cancellation message is valid only while waiting for players");
        }
    }

    public boolean lobbyControlsAllowed() {
        return phase == LobbyMatchPhase.WAITING_FOR_PLAYERS
                || phase == LobbyMatchPhase.START_COUNTDOWN;
    }
}
