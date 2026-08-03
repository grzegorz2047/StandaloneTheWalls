package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Optional;

/** Stable public outcome codes for authoritative lobby commands. */
public enum LobbyCommandOutcome {
    APPLIED(1),
    NO_CHANGE(2),
    LOBBY_FULL(10),
    DUPLICATE_PARTICIPANT(11),
    UNKNOWN_PARTICIPANT(12),
    TEAM_DISABLED(13),
    TEAM_FULL(14),
    TEAM_IMBALANCE(15),
    TEAM_REQUIRED(16),
    MATCH_ALREADY_STARTED(17);

    private final int wireCode;

    LobbyCommandOutcome(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static Optional<LobbyCommandOutcome> fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 1 -> Optional.of(APPLIED);
            case 2 -> Optional.of(NO_CHANGE);
            case 10 -> Optional.of(LOBBY_FULL);
            case 11 -> Optional.of(DUPLICATE_PARTICIPANT);
            case 12 -> Optional.of(UNKNOWN_PARTICIPANT);
            case 13 -> Optional.of(TEAM_DISABLED);
            case 14 -> Optional.of(TEAM_FULL);
            case 15 -> Optional.of(TEAM_IMBALANCE);
            case 16 -> Optional.of(TEAM_REQUIRED);
            case 17 -> Optional.of(MATCH_ALREADY_STARTED);
            default -> Optional.empty();
        };
    }
}
