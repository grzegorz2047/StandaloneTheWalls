package pl.grzegorz2047.standalonethewalls.transport.bctls;

import org.bouncycastle.jsse.BCSSLConnection;
import org.bouncycastle.jsse.BCSSLSocket;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;

/** Reads tls-exporter only while BCJSSE invokes its handshake-completed callback. */
final class TlsChannelBindingExporter {
    private static final String TLS_EXPORTER = "tls-exporter";

    private TlsChannelBindingExporter() {
        throw new AssertionError("No instances");
    }

    static SecureChannelBinding exportDuringHandshakeCallback(BCSSLSocket socket)
            throws TlsTransportException {
        BCSSLConnection connection = socket.getConnection();
        if (connection == null) {
            throw new TlsTransportException(
                    TlsTransportException.Code.HANDSHAKE_NOT_COMPLETE,
                    "BCJSSE did not expose the completed TLS connection");
        }

        try {
            byte[] exported = connection.getChannelBinding(TLS_EXPORTER);
            if (exported == null) {
                throw new TlsTransportException(
                        TlsTransportException.Code.CHANNEL_BINDING_UNAVAILABLE,
                        "BCJSSE did not provide the tls-exporter channel binding");
            }
            return new SecureChannelBinding(exported);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.CHANNEL_BINDING_UNAVAILABLE,
                    "the tls-exporter channel binding is unavailable or invalid",
                    exception);
        }
    }
}
