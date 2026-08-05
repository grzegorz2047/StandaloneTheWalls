package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.util.Optional;

/** Stable public rejection codes without store, capacity, or exception details. */
public enum RealtimeTicketRejection {
    UNSUPPORTED_PROFILE(1),
    ALREADY_ISSUED_FOR_ROUND(2),
    TEMPORARILY_UNAVAILABLE(3),
    SERVER_SHUTTING_DOWN(4);

    private final int wireId;

    RealtimeTicketRejection(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<RealtimeTicketRejection> fromWireId(int wireId) {
        return switch (wireId) {
            case 1 -> Optional.of(UNSUPPORTED_PROFILE);
            case 2 -> Optional.of(ALREADY_ISSUED_FOR_ROUND);
            case 3 -> Optional.of(TEMPORARILY_UNAVAILABLE);
            case 4 -> Optional.of(SERVER_SHUTTING_DOWN);
            default -> Optional.empty();
        };
    }
}
