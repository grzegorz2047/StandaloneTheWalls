package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

/** Enforces explicit Ed25519 TOFU or an externally supplied expected server pin. */
public final class PinnedServerTrustManager extends X509ExtendedTrustManager {
    private static final String SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1";
    private static final X509Certificate[] NO_ACCEPTED_ISSUERS = new X509Certificate[0];

    private final ServerTrustService trustService;
    private final ServerReference reference;
    private final Optional<ServerId> expectedPin;

    public PinnedServerTrustManager(
            ServerTrustService trustService,
            ServerReference reference,
            Optional<ServerId> expectedPin) {
        this.trustService = Objects.requireNonNull(trustService, "trustService");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.expectedPin = Objects.requireNonNull(expectedPin, "expectedPin");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        rejectClientCertificateAuthentication();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        verifyServer(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return NO_ACCEPTED_ISSUERS.clone();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        rejectClientCertificateAuthentication();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        verifyServer(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        rejectClientCertificateAuthentication();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        verifyServer(chain);
    }

    private void verifyServer(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("the server did not present a certificate");
        }

        X509Certificate leaf = chain[0];
        leaf.checkValidity();
        requireServerUsage(leaf);

        byte[] publicKey = leaf.getPublicKey().getEncoded();
        try {
            ServerId presented = ServerId.fromPublicKey(publicKey);
            ServerFingerprint fingerprint = ServerFingerprint.fromPublicKey(publicKey);
            ServerTrustDecision decision = trustService.inspect(reference, presented, expectedPin);
            if (decision.status() != ServerTrustDecision.Status.TRUSTED) {
                throw new TlsTrustException(decision.status(), reference, presented, fingerprint);
            }
        } catch (IdentityException exception) {
            throw new CertificateException(
                    "the server certificate does not contain a valid Ed25519 identity", exception);
        } catch (ServerTrustStoreException exception) {
            throw new CertificateException("the server trust store could not be read", exception);
        }
    }

    private static void requireServerUsage(X509Certificate certificate)
            throws CertificateException {
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && (keyUsage.length == 0 || !keyUsage[0])) {
            throw new CertificateException(
                    "the server certificate does not allow digital signatures");
        }
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        if (extendedKeyUsage != null && !extendedKeyUsage.contains(SERVER_AUTH_OID)) {
            throw new CertificateException(
                    "the server certificate is not valid for TLS server authentication");
        }
    }

    private static void rejectClientCertificateAuthentication() throws CertificateException {
        throw new CertificateException("client certificate authentication is not configured");
    }
}
