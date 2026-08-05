package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;

/** Stable client-local failure before ownership of a server result is transferred. */
public final class RealtimeTicketRequestException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public RealtimeTicketRequestException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        SEND_FAILED,
        SESSION_TERMINATED
    }
}
