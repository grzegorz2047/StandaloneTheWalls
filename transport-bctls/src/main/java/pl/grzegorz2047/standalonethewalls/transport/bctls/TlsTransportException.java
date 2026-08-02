package pl.grzegorz2047.standalonethewalls.transport.bctls;

/** Bounded secure-transport failure that never includes key or channel-binding bytes. */
public final class TlsTransportException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    public TlsTransportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public TlsTransportException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        CRYPTO_INITIALIZATION_FAILED,
        SERVER_CREDENTIALS_INVALID,
        SOCKET_CONFIGURATION_INVALID,
        HANDSHAKE_FAILED,
        CHANNEL_BINDING_UNAVAILABLE,
        PEER_IDENTITY_UNAVAILABLE
    }
}
