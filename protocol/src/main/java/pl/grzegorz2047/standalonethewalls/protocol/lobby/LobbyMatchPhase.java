package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Optional;

/** Stable wire phases exposed by the lobby-to-preparation protocol slice. */
public enum LobbyMatchPhase {
    WAITING_FOR_PLAYERS(1),
    START_COUNTDOWN(2),
    PREPARATION(3),
    WALLS_OPENING(4),
    OPEN_COMBAT(5);

    private final int wireCode;

    LobbyMatchPhase(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static Optional<LobbyMatchPhase> fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 1 -> Optional.of(WAITING_FOR_PLAYERS);
            case 2 -> Optional.of(START_COUNTDOWN);
            case 3 -> Optional.of(PREPARATION);
            case 4 -> Optional.of(WALLS_OPENING);
            case 5 -> Optional.of(OPEN_COMBAT);
            default -> Optional.empty();
        };
    }
}
