package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import org.bouncycastle.tls.CertificateEntry;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

/** Immutable Ed25519 private key and X.509 chain used by one dedicated server. */
public final class Tls13ServerCredentials {
    private static final int MAXIMUM_CHAIN_LENGTH = 8;

    private final PrivateKey privateKey;
    private final X509Certificate[] certificateChain;
    private final ServerId serverId;

    private Tls13ServerCredentials(
            PrivateKey privateKey, X509Certificate[] certificateChain, ServerId serverId) {
        this.privateKey = privateKey;
        this.certificateChain = certificateChain;
        this.serverId = serverId;
    }

    public static Tls13ServerCredentials create(
            PrivateKey privateKey, List<X509Certificate> certificateChain)
            throws TlsTransportException {
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(certificateChain, "certificateChain");
        if (!"Ed25519".equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw invalid("the server private key must use Ed25519");
        }
        if (certificateChain.isEmpty() || certificateChain.size() > MAXIMUM_CHAIN_LENGTH) {
            throw invalid("the server certificate chain must contain between 1 and 8 entries");
        }

        X509Certificate[] copy = certificateChain.toArray(X509Certificate[]::new);
        for (X509Certificate certificate : copy) {
            Objects.requireNonNull(certificate, "certificateChain entry");
        }
        X509Certificate leaf = copy[0];
        if (!"Ed25519".equalsIgnoreCase(leaf.getPublicKey().getAlgorithm())) {
            throw invalid("the server leaf certificate must contain an Ed25519 public key");
        }
        try {
            leaf.checkValidity();
            return new Tls13ServerCredentials(
                    privateKey, copy, ServerId.fromPublicKey(leaf.getPublicKey().getEncoded()));
        } catch (java.security.cert.CertificateException | IdentityException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.SERVER_CREDENTIALS_INVALID,
                    "the server certificate chain is invalid",
                    exception);
        }
    }

    public ServerId serverId() {
        return serverId;
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    org.bouncycastle.tls.Certificate toTlsCertificate(JcaTlsCrypto crypto)
            throws TlsTransportException {
        CertificateEntry[] entries = new CertificateEntry[certificateChain.length];
        try {
            for (int index = 0; index < certificateChain.length; index++) {
                TlsCertificate certificate =
                        crypto.createCertificate(certificateChain[index].getEncoded());
                entries[index] = new CertificateEntry(certificate, null);
            }
            return new org.bouncycastle.tls.Certificate(TlsUtils.EMPTY_BYTES, entries);
        } catch (CertificateEncodingException | java.io.IOException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.SERVER_CREDENTIALS_INVALID,
                    "the server certificate chain cannot be encoded for TLS",
                    exception);
        }
    }

    private static TlsTransportException invalid(String message) {
        return new TlsTransportException(
                TlsTransportException.Code.SERVER_CREDENTIALS_INVALID, message);
    }
}
