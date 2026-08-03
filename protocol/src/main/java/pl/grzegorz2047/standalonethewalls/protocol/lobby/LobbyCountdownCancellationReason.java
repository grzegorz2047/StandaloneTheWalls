package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Optional;

/** Bounded public reason for returning from countdown to the waiting lobby. */
public enum LobbyCountdownCancellationReason {
    NONE(0),
    INSUFFICIENT_PLAYERS(1),
    LOBBY_NOT_READY(2);

    private final int wireCode;

    LobbyCountdownCancellationReason(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static Optional<LobbyCountdownCancellationReason> fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> Optional.of(NONE);
            case 1 -> Optional.of(INSUFFICIENT_PLAYERS);
            case 2 -> Optional.of(LOBBY_NOT_READY);
            default -> Optional.empty();
        };
    }
}
