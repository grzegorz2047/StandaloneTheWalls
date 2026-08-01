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
        CONTEXT_INITIALIZATION_FAILED,
        NO_ALLOWED_CIPHER_SUITE,
        NEGOTIATED_PROTOCOL_REJECTED,
        NEGOTIATED_CIPHER_REJECTED,
        NEGOTIATED_APPLICATION_PROTOCOL_REJECTED,
        UNSUPPORTED_JSSE_SOCKET,
        HANDSHAKE_NOT_COMPLETE,
        CHANNEL_BINDING_UNAVAILABLE,
        PEER_IDENTITY_UNAVAILABLE
    }
}
