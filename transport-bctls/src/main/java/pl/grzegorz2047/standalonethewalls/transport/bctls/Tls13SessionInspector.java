package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Validates a completed socket and extracts the identity and RFC 9266 channel binding. */
public final class Tls13SessionInspector {
    private Tls13SessionInspector() {
        throw new AssertionError("No instances");
    }

    public static Tls13SessionSecurity inspect(SSLSocket socket) throws TlsTransportException {
        Objects.requireNonNull(socket, "socket");
        Tls13Policy.verifyNegotiated(socket);
        SSLSession session = socket.getSession();
        try {
            Certificate[] peerCertificates = session.getPeerCertificates();
            if (peerCertificates.length == 0
                    || !(peerCertificates[0] instanceof X509Certificate leaf)) {
                throw new TlsTransportException(
                        TlsTransportException.Code.PEER_IDENTITY_UNAVAILABLE,
                        "the TLS peer did not present an X.509 server certificate");
            }
            ServerId serverId = ServerId.fromPublicKey(leaf.getPublicKey().getEncoded());
            return new Tls13SessionSecurity(
                    serverId,
                    TlsChannelBindingExporter.export(socket),
                    session.getCipherSuite(),
                    socket.getApplicationProtocol());
        } catch (SSLPeerUnverifiedException | IdentityException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.PEER_IDENTITY_UNAVAILABLE,
                    "the TLS peer identity could not be verified",
                    exception);
        }
    }
}
