package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
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

        KeyManager keyManager = new TestServerKeyManager(keyPair.getPrivate(), certificate);
        return new TestCertificateMaterial(keyPair, certificate, new KeyManager[] {keyManager});
    }

    private static final class TestServerKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "server";
        private final PrivateKey privateKey;
        private final java.security.cert.X509Certificate[] certificateChain;

        private TestServerKeyManager(
                PrivateKey privateKey, java.security.cert.X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificateChain = new java.security.cert.X509Certificate[] {certificate};
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return supports(keyType) ? new String[] {ALIAS} : null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return supports(keyType) ? ALIAS : null;
        }

        @Override
        public java.security.cert.X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? certificateChain.clone() : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? privateKey : null;
        }

        @Override
        public String chooseEngineClientAlias(
                String[] keyTypes, Principal[] issuers, SSLEngine engine) {
            return null;
        }

        @Override
        public String chooseEngineServerAlias(
                String keyType, Principal[] issuers, SSLEngine engine) {
            return supports(keyType) ? ALIAS : null;
        }

        private static boolean supports(String keyType) {
            return "Ed25519".equalsIgnoreCase(keyType)
                    || "EdDSA".equalsIgnoreCase(keyType)
                    || "EdEC".equalsIgnoreCase(keyType);
        }
    }
}
