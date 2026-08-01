package pl.grzegorz2047.standalonethewalls.protocol.identity;

/** Persistence adapter failure that does not expose trust-store contents. */
public final class ServerTrustStoreException extends Exception {
    private static final long serialVersionUID = 1L;

    public ServerTrustStoreException(String message) {
        super(message);
    }

    public ServerTrustStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
