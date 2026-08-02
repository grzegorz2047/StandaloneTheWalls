package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Optional;

/** Stable post-authentication server admission status sent before lobby ownership. */
public enum PlayerSessionAdmissionStatus {
    GLOBAL_ACCEPTED(1, true),
    LOCAL_FIRST_USE_ACCEPTED(2, true),
    LOCAL_RETURNING_ACCEPTED(3, true),
    PLAYER_BANNED(10, false),
    REGISTRY_UNAVAILABLE(11, false),
    REGISTRY_STALE(12, false),
    UNKNOWN_GLOBAL_HANDLE(13, false),
    REVOKED_GLOBAL_HANDLE(14, false),
    GLOBAL_PLAYER_MISMATCH(15, false),
    LOCAL_BINDING_CONFLICT(16, false),
    LOCAL_BINDING_CAPACITY_EXCEEDED(17, false),
    SERVER_CAPACITY_EXCEEDED(18, false),
    SERVER_SHUTTING_DOWN(19, false);

    private final int wireId;
    private final boolean accepted;

    PlayerSessionAdmissionStatus(int wireId, boolean accepted) {
        this.wireId = wireId;
        this.accepted = accepted;
    }

    public int wireId() {
        return wireId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public static Optional<PlayerSessionAdmissionStatus> fromWireId(int wireId) {
        return switch (wireId) {
            case 1 -> Optional.of(GLOBAL_ACCEPTED);
            case 2 -> Optional.of(LOCAL_FIRST_USE_ACCEPTED);
            case 3 -> Optional.of(LOCAL_RETURNING_ACCEPTED);
            case 10 -> Optional.of(PLAYER_BANNED);
            case 11 -> Optional.of(REGISTRY_UNAVAILABLE);
            case 12 -> Optional.of(REGISTRY_STALE);
            case 13 -> Optional.of(UNKNOWN_GLOBAL_HANDLE);
            case 14 -> Optional.of(REVOKED_GLOBAL_HANDLE);
            case 15 -> Optional.of(GLOBAL_PLAYER_MISMATCH);
            case 16 -> Optional.of(LOCAL_BINDING_CONFLICT);
            case 17 -> Optional.of(LOCAL_BINDING_CAPACITY_EXCEEDED);
            case 18 -> Optional.of(SERVER_CAPACITY_EXCEEDED);
            case 19 -> Optional.of(SERVER_SHUTTING_DOWN);
            default -> Optional.empty();
        };
    }
}
