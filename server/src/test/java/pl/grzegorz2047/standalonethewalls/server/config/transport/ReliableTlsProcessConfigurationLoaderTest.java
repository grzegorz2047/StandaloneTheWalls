package pl.grzegorz2047.standalonethewalls.server.config.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfiguration;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;

class ReliableTlsProcessConfigurationLoaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsCanonicalCredentialsWithoutBindingTheConfiguredPort() throws Exception {
        ServerTlsTestCertificateMaterial material =
                ServerTlsTestCertificateMaterial.create(1L);
        writeCredentials(material, "server-key.pk8", "server-certificate.der");

        try (ServerSocket occupied = new ServerSocket(0)) {
            ServerConfiguration server =
                    new ServerConfiguration(
                            "Test", 20, occupied.getLocalPort(), 27421, 40);
            Path configuration =
                    writeConfiguration(
                            "transport.schema=1\n"
                                    + "transport.reliable.bind-address=127.0.0.1\n"
                                    + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                    + "transport.reliable.certificate-x509-path=server-certificate.der\n"
                                    + "transport.reliable.maximum-active-connections=20\n"
                                    + "transport.identity.maximum-outstanding-challenges=20\n");

            ReliableTlsProcessConfiguration loaded =
                    ReliableTlsProcessConfigurationLoader.load(configuration, server);

            assertThat(loaded.listenerConfig().bindAddress().getAddress().getHostAddress())
                    .isEqualTo("127.0.0.1");
            assertThat(loaded.listenerConfig().bindAddress().getPort())
                    .isEqualTo(occupied.getLocalPort());
            assertThat(loaded.listenerConfig().maximumActiveConnections()).isEqualTo(20);
            assertThat(loaded.credentials().serverId())
                    .isEqualTo(ServerId.fromPublicKey(material.keyPair().getPublic().getEncoded()));
        }
    }

    @Test
    void rejectsMismatchedKeysUnknownFieldsAndCapacityAboveServerLimit() throws Exception {
        ServerTlsTestCertificateMaterial first =
                ServerTlsTestCertificateMaterial.create(2L);
        ServerTlsTestCertificateMaterial second =
                ServerTlsTestCertificateMaterial.create(3L);
        Files.write(temporaryDirectory.resolve("server-key.pk8"), first.keyPair().getPrivate().getEncoded());
        Files.write(
                temporaryDirectory.resolve("server-certificate.der"),
                second.certificate().getEncoded());
        ServerConfiguration server = new ServerConfiguration("Test", 20, 27420, 27421, 10);

        Path mismatch =
                writeConfiguration(
                        "transport.schema=1\n"
                                + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                + "transport.reliable.certificate-x509-path=server-certificate.der\n");
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        mismatch, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");

        writeCredentials(first, "server-key.pk8", "server-certificate.der");
        Path unknown =
                writeConfiguration(
                        "transport.schema=1\n"
                                + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                + "transport.reliable.certificate-x509-path=server-certificate.der\n"
                                + "transport.reliable.fallback-plaintext=true\n");
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        unknown, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown TLS configuration key");

        Path excessive =
                writeConfiguration(
                        "transport.schema=1\n"
                                + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                + "transport.reliable.certificate-x509-path=server-certificate.der\n"
                                + "transport.reliable.maximum-active-connections=11\n");
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        excessive, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed server.maximum-players");
    }

    @Test
    void rejectsTrailingCertificateDataDuplicateKeysAndOversizedCredentialFiles()
            throws Exception {
        ServerTlsTestCertificateMaterial material =
                ServerTlsTestCertificateMaterial.create(4L);
        writeCredentials(material, "server-key.pk8", "server-certificate.der");
        Files.write(
                temporaryDirectory.resolve("server-certificate.der"),
                new byte[] {0},
                java.nio.file.StandardOpenOption.APPEND);
        ServerConfiguration server = ServerConfiguration.defaults();
        Path configuration =
                writeConfiguration(
                        "transport.schema=1\n"
                                + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                + "transport.reliable.certificate-x509-path=server-certificate.der\n");
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        configuration, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical X.509 DER");

        writeCredentials(material, "server-key.pk8", "server-certificate.der");
        Path duplicate =
                writeConfiguration(
                        "transport.schema=1\n"
                                + "transport.schema=1\n"
                                + "transport.reliable.private-key-pkcs8-path=server-key.pk8\n"
                                + "transport.reliable.certificate-x509-path=server-certificate.der\n");
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        duplicate, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate TLS configuration key");

        Files.write(
                temporaryDirectory.resolve("server-key.pk8"),
                new byte[ReliableTlsProcessConfigurationLoader.MAXIMUM_PRIVATE_KEY_BYTES + 1]);
        assertThatThrownBy(
                        () ->
                                ReliableTlsProcessConfigurationLoader.load(
                                        configuration, server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum byte size");
    }

    private Path writeConfiguration(String content) throws Exception {
        Path path = temporaryDirectory.resolve("tls.properties");
        Files.writeString(path, content);
        return path;
    }

    private void writeCredentials(
            ServerTlsTestCertificateMaterial material, String privateKeyName, String certificateName)
            throws Exception {
        Files.write(
                temporaryDirectory.resolve(privateKeyName),
                material.keyPair().getPrivate().getEncoded());
        Files.write(
                temporaryDirectory.resolve(certificateName), material.certificate().getEncoded());
    }
}
