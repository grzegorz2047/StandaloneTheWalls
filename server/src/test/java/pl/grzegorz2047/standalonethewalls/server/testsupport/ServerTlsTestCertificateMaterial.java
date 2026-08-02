package pl.grzegorz2047.standalonethewalls.server.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BouncyCastleTlsCryptoFactory;

/** Reusable self-signed Ed25519 server credential fixture for process-composition tests. */
public final class ServerTlsTestCertificateMaterial {
    private final KeyPair keyPair;
    private final byte[] certificateDer;

    private ServerTlsTestCertificateMaterial(KeyPair keyPair, byte[] certificateDer) {
        this.keyPair = Objects.requireNonNull(keyPair, "keyPair");
        this.certificateDer = Objects.requireNonNull(certificateDer, "certificateDer").clone();
    }

    public static ServerTlsTestCertificateMaterial create(long serial)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        Provider provider = BouncyCastleTlsCryptoFactory.provider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", provider);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Sunderfront Process Test Server");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.valueOf(serial),
                        Date.from(now.minus(Duration.ofMinutes(1))),
                        Date.from(now.plus(Duration.ofDays(30))),
                        subject,
                        keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        ContentSigner signer =
                new JcaContentSignerBuilder("Ed25519")
                        .setProvider(provider)
                        .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate =
                new JcaX509CertificateConverter().setProvider(provider).getCertificate(holder);
        certificate.verify(keyPair.getPublic(), provider);
        return new ServerTlsTestCertificateMaterial(keyPair, certificate.getEncoded());
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public byte[] certificateDer() {
        return certificateDer.clone();
    }

    public X509Certificate certificate() throws CertificateException {
        CertificateFactory factory =
                CertificateFactory.getInstance("X.509", BouncyCastleTlsCryptoFactory.provider());
        return (X509Certificate)
                factory.generateCertificate(new ByteArrayInputStream(certificateDer));
    }
}
