package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.util.Objects;

/** Stable bounded failures from ticket issue or redemption. */
public final class RealtimeTicketStoreException extends Exception {
    private final Code code;

    public RealtimeTicketStoreException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        CLOSED,
        CAPACITY_EXHAUSTED,
        INVALID_LIFETIME,
        INVALID_ENTROPY,
        IDENTITY_COLLISION_LIMIT
    }
}
