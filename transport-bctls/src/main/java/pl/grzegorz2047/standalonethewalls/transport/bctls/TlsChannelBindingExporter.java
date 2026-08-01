package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.jsse.BCSSLConnection;
import org.bouncycastle.jsse.BCSSLSocket;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;

/** Exports the RFC 9266 tls-exporter binding from a completed BCJSSE connection. */
public final class TlsChannelBindingExporter {
    private static final String TLS_EXPORTER = "tls-exporter";

    private TlsChannelBindingExporter() {
        throw new AssertionError("No instances");
    }

    public static SecureChannelBinding export(SSLSocket socket) throws TlsTransportException {
        Objects.requireNonNull(socket, "socket");
        if (!(socket instanceof BCSSLSocket bcSocket)) {
            throw new TlsTransportException(
                    TlsTransportException.Code.UNSUPPORTED_JSSE_SOCKET,
                    "the socket is not backed by BCJSSE");
        }

        BCSSLConnection connection = bcSocket.getConnection();
        if (connection == null) {
            throw new TlsTransportException(
                    TlsTransportException.Code.HANDSHAKE_NOT_COMPLETE,
                    "the TLS handshake has not completed");
        }

        byte[] exported = connection.getChannelBinding(TLS_EXPORTER);
        if (exported == null) {
            throw new TlsTransportException(
                    TlsTransportException.Code.CHANNEL_BINDING_UNAVAILABLE,
                    "BCJSSE did not provide the tls-exporter channel binding");
        }
        try {
            return new SecureChannelBinding(exported);
        } catch (IllegalArgumentException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.CHANNEL_BINDING_UNAVAILABLE,
                    "the tls-exporter channel binding has an invalid length",
                    exception);
        }
    }
}
