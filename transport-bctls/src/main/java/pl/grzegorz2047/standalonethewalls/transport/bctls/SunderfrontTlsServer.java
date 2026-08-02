package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.util.Objects;
import java.util.Vector;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;

/** Low-level TLS 1.3 server peer using one explicit Ed25519 certificate identity. */
final class SunderfrontTlsServer extends DefaultTlsServer {
    private final JcaTlsCrypto crypto;
    private final Tls13ServerCredentials credentials;
    private Tls13SessionSecurity security;

    SunderfrontTlsServer(JcaTlsCrypto crypto, Tls13ServerCredentials credentials) {
        super(Objects.requireNonNull(crypto, "crypto"));
        this.crypto = crypto;
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return Tls13ProtocolPolicy.supportedVersions();
    }

    @Override
    protected int[] getSupportedCipherSuites() {
        return Tls13ProtocolPolicy.supportedCipherSuites();
    }

    @Override
    protected Vector<ProtocolName> getProtocolNames() {
        return Tls13ProtocolPolicy.protocolNames();
    }

    @Override
    public TlsCredentials getCredentials() throws IOException {
        try {
            return new JcaDefaultTlsCredentialedSigner(
                    new TlsCryptoParameters(context),
                    crypto,
                    credentials.privateKey(),
                    credentials.toTlsCertificate(crypto),
                    SignatureAndHashAlgorithm.ed25519);
        } catch (TlsTransportException exception) {
            throw new TlsFatalAlert(AlertDescription.internal_error, exception);
        }
    }

    @Override
    public CertificateRequest getCertificateRequest() {
        return null;
    }

    @Override
    public void notifyHandshakeComplete() throws IOException {
        super.notifyHandshakeComplete();
        security = Tls13SecurityCapture.capture(context, credentials.serverId());
    }

    Tls13SessionSecurity security() {
        if (security == null) {
            throw new IllegalStateException("the TLS server handshake has not completed");
        }
        return security;
    }
}
