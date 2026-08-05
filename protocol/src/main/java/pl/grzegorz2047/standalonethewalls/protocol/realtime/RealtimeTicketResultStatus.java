package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.util.Optional;

/** Wire-level result variant for realtime ticket provisioning. */
public enum RealtimeTicketResultStatus {
    ISSUED(1),
    REJECTED(2);

    private final int wireId;

    RealtimeTicketResultStatus(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<RealtimeTicketResultStatus> fromWireId(int wireId) {
        return switch (wireId) {
            case 1 -> Optional.of(ISSUED);
            case 2 -> Optional.of(REJECTED);
            default -> Optional.empty();
        };
    }
}
