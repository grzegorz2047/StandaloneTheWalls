package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
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

record TestCertificateMaterial(KeyPair keyPair, java.security.cert.X509Certificate certificate) {
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
        return new TestCertificateMaterial(keyPair, certificate);
    }
}
