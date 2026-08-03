package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Optional;

/** Stable protocol-level team values independent of the game-domain module. */
public enum LobbyTeam {
    UNASSIGNED(0),
    GREEN(1),
    BLUE(2),
    RED(3),
    YELLOW(4);

    private final int wireCode;

    LobbyTeam(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static Optional<LobbyTeam> fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> Optional.of(UNASSIGNED);
            case 1 -> Optional.of(GREEN);
            case 2 -> Optional.of(BLUE);
            case 3 -> Optional.of(RED);
            case 4 -> Optional.of(YELLOW);
            default -> Optional.empty();
        };
    }
}
