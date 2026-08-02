package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Vector;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ProtocolName;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.ServerOnlyTlsAuthentication;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Low-level TLS 1.3 client peer with explicit pinned-server certificate validation. */
final class SunderfrontTlsClient extends DefaultTlsClient {
    private final PinnedServerTrustManager trustManager;
    private ServerId serverId;
    private Tls13SessionSecurity security;

    SunderfrontTlsClient(JcaTlsCrypto crypto, PinnedServerTrustManager trustManager) {
        super(Objects.requireNonNull(crypto, "crypto"));
        this.trustManager = Objects.requireNonNull(trustManager, "trustManager");
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
    public TlsAuthentication getAuthentication() {
        return new ServerOnlyTlsAuthentication() {
            @Override
            public void notifyServerCertificate(TlsServerCertificate serverCertificate)
                    throws IOException {
                verifyServerCertificate(serverCertificate);
            }
        };
    }

    @Override
    public void notifyHandshakeComplete() throws IOException {
        super.notifyHandshakeComplete();
        if (serverId == null) {
            throw new TlsFatalAlert(
                    AlertDescription.internal_error,
                    "the verified server identity is unavailable");
        }
        security = Tls13SecurityCapture.capture(context, serverId);
    }

    Tls13SessionSecurity security() {
        if (security == null) {
            throw new IllegalStateException("the TLS client handshake has not completed");
        }
        return security;
    }

    private void verifyServerCertificate(TlsServerCertificate serverCertificate)
            throws IOException {
        org.bouncycastle.tls.Certificate chain = serverCertificate.getCertificate();
        if (chain == null || chain.isEmpty() || chain.getLength() > 8) {
            throw new TlsFatalAlert(
                    AlertDescription.bad_certificate,
                    "the server certificate chain length is invalid");
        }

        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate[] certificates = new X509Certificate[chain.getLength()];
            for (int index = 0; index < certificates.length; index++) {
                TlsCertificate tlsCertificate = chain.getCertificateAt(index);
                certificates[index] =
                        (X509Certificate)
                                certificateFactory.generateCertificate(
                                        new ByteArrayInputStream(tlsCertificate.getEncoded()));
            }
            trustManager.checkServerTrusted(certificates, "Ed25519");
            serverId =
                    ServerId.fromPublicKey(certificates[0].getPublicKey().getEncoded());
        } catch (GeneralSecurityException | IdentityException exception) {
            throw new TlsFatalAlert(AlertDescription.bad_certificate, exception);
        }
    }
}
