package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
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

record TestCertificateMaterial(
        KeyPair keyPair, java.security.cert.X509Certificate certificate, KeyManager[] keyManagers) {
    private static final String PASSWORD = "sunderfront-test";

    TestCertificateMaterial {
        keyManagers = keyManagers.clone();
    }

    @Override
    public KeyManager[] keyManagers() {
        return keyManagers.clone();
    }

    static TestCertificateMaterial create(Provider provider, long serial)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", provider);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Sunderfront Test Server");
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
        java.security.cert.X509Certificate certificate =
                new JcaX509CertificateConverter().setProvider(provider).getCertificate(holder);
        certificate.verify(keyPair.getPublic(), provider);

        char[] password = PASSWORD.toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry(
                    "server",
                    keyPair.getPrivate(),
                    password,
                    new java.security.cert.Certificate[] {certificate});
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            return new TestCertificateMaterial(
                    keyPair, certificate, keyManagerFactory.getKeyManagers());
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
