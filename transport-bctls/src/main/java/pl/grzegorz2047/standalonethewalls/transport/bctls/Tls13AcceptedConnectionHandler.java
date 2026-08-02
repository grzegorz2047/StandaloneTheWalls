package pl.grzegorz2047.standalonethewalls.transport.bctls;

/** Receives one authenticated active-connection lease outside the accept thread. */
@FunctionalInterface
public interface Tls13AcceptedConnectionHandler {
    /** The handler owns the lease after this method returns normally. */
    void onAccepted(AcceptedTlsConnection connection);
}
