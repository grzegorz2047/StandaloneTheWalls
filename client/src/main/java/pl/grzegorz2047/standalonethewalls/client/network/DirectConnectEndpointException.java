package pl.grzegorz2047.standalonethewalls.client.network;

/** Stable validation failure for a user-supplied Direct Connect endpoint. */
public final class DirectConnectEndpointException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public DirectConnectEndpointException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        EMPTY,
        TOO_LONG,
        WHITESPACE,
        INVALID_SYNTAX,
        IPV6_REQUIRES_BRACKETS,
        INVALID_HOST,
        INVALID_PORT,
        PORT_OUT_OF_RANGE
    }
}
