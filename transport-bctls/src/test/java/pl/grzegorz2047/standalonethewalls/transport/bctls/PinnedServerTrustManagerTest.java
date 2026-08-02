package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.util.Optional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class PinnedServerTrustManagerTest {
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void firstUseIsReadOnlyUntilExplicitConfirmation()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    CertificateException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService service = new ServerTrustService(store);
        PinnedServerTrustManager manager =
                new PinnedServerTrustManager(service, REFERENCE, Optional.empty());
        ServerId serverId = ServerId.fromPublicKey(material.keyPair().getPublic().getEncoded());
        ServerFingerprint fingerprint =
                ServerFingerprint.fromPublicKey(material.keyPair().getPublic().getEncoded());

        assertThatThrownBy(
                        () ->
                                manager.checkServerTrusted(
                                        new java.security.cert.X509Certificate[] {
                                            material.certificate()
                                        },
                                        "Ed25519"))
                .isInstanceOfSatisfying(
                        TlsTrustException.class,
                        exception -> {
                            assertThat(exception.status())
                                    .isEqualTo(
                                            ServerTrustDecision.Status
                                                    .FIRST_USE_REQUIRES_CONFIRMATION);
                            assertThat(exception.presentedServerId()).isEqualTo(serverId);
                            assertThat(exception.fingerprint()).isEqualTo(fingerprint);
                            assertThat(exception.getMessage())
                                    .doesNotContain(
                                            java.util.Base64.getEncoder()
                                                    .encodeToString(
                                                            material.keyPair()
                                                                    .getPublic()
                                                                    .getEncoded()));
                        });
        assertThat(store.find(REFERENCE)).isEmpty();

        service.confirmFirstUse(REFERENCE, serverId, Optional.empty(), "test confirmation");
        manager.checkServerTrusted(
                new java.security.cert.X509Certificate[] {material.certificate()}, "Ed25519");
        assertThat(store.find(REFERENCE)).isPresent();
    }

    @Test
    void changedIdentityAndExpectedPinMismatchFailClosed()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException {
        TestCertificateMaterial first = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        TestCertificateMaterial replacement = TestCertificateMaterial.create(CRYPTO_PROVIDER, 2L);
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService service = new ServerTrustService(store);
        ServerId firstId = ServerId.fromPublicKey(first.keyPair().getPublic().getEncoded());
        ServerId replacementId =
                ServerId.fromPublicKey(replacement.keyPair().getPublic().getEncoded());
        service.confirmFirstUse(REFERENCE, firstId, Optional.empty(), "test confirmation");

        PinnedServerTrustManager tofu =
                new PinnedServerTrustManager(service, REFERENCE, Optional.empty());
        assertThatThrownBy(
                        () ->
                                tofu.checkServerTrusted(
                                        new java.security.cert.X509Certificate[] {
                                            replacement.certificate()
                                        },
                                        "Ed25519"))
                .isInstanceOfSatisfying(
                        TlsTrustException.class,
                        exception ->
                                assertThat(exception.status())
                                        .isEqualTo(ServerTrustDecision.Status.CHANGED_IDENTITY));
        assertThat(store.find(REFERENCE).orElseThrow().serverId()).isEqualTo(firstId);

        PinnedServerTrustManager expected =
                new PinnedServerTrustManager(service, REFERENCE, Optional.of(replacementId));
        assertThatThrownBy(
                        () ->
                                expected.checkServerTrusted(
                                        new java.security.cert.X509Certificate[] {
                                            first.certificate()
                                        },
                                        "Ed25519"))
                .isInstanceOfSatisfying(
                        TlsTrustException.class,
                        exception ->
                                assertThat(exception.status())
                                        .isEqualTo(
                                                ServerTrustDecision.Status.EXPECTED_PIN_MISMATCH));
    }

    @Test
    void rejectsAClientCertificatePathBecauseMutualTlsIsNotConfigured() {
        PinnedServerTrustManager manager =
                new PinnedServerTrustManager(
                        new ServerTrustService(new InMemoryServerTrustStore()),
                        REFERENCE,
                        Optional.empty());

        assertThatThrownBy(
                        () ->
                                manager.checkClientTrusted(
                                        new java.security.cert.X509Certificate[0], "Ed25519"))
                .isInstanceOf(java.security.cert.CertificateException.class)
                .hasMessageContaining("not configured");
    }
}
