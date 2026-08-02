package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.server.config.transport.ReliableTlsProcessConfiguration;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntimeConfiguration;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerCredentials;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListenerConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsTransportException;

class ReliableTlsAdmissionRuntimeTest {
    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void startsAndClosesOneListenerBackedByTheSharedIdentityRuntime()
            throws IOException,
                    GeneralSecurityException,
                    OperatorCreationException,
                    TlsTransportException {
        int port = freePort();
        LocalIdentityRuntime identityRuntime = identityRuntime();
        ReliableTlsProcessConfiguration configuration = configuration(port, 10L);

        ReliableTlsAdmissionRuntime runtime =
                ReliableTlsAdmissionRuntime.open(configuration, identityRuntime, Clock.systemUTC());
        runtime.start();

        assertThat(runtime.isRunning()).isTrue();
        assertThat(runtime.localAddress().getPort()).isEqualTo(port);
        assertThat(runtime.authorizedSessions().size()).isZero();

        runtime.close();
        runtime.close();
        assertThat(runtime.isRunning()).isFalse();
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertThat(rebound.isBound()).isTrue();
        }
    }

    @Test
    void occupiedPortRollsBackGatewayAndNeverInvokesTerminalFailureHandler()
            throws IOException,
                    GeneralSecurityException,
                    OperatorCreationException,
                    TlsTransportException {
        LocalIdentityRuntime identityRuntime = identityRuntime();
        AtomicBoolean terminalFailure = new AtomicBoolean();
        try (ServerSocket occupied = new ServerSocket(0)) {
            ReliableTlsProcessConfiguration configuration =
                    configuration(occupied.getLocalPort(), 11L);

            assertThatThrownBy(
                            () ->
                                    ReliableTlsAdmissionRuntime.open(
                                            configuration,
                                            identityRuntime,
                                            Clock.systemUTC(),
                                            () -> terminalFailure.set(true)))
                    .isInstanceOf(IOException.class);
            assertThat(terminalFailure).isFalse();
        }
    }

    private LocalIdentityRuntime identityRuntime() throws GeneralSecurityException {
        KeyPair root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        return LocalIdentityRuntime.open(
                new LocalIdentityRuntimeConfiguration(
                        temporaryDirectory.resolve("identity.sqlite"),
                        temporaryDirectory.resolve("registry.sfrb"),
                        HandleAuthorizationMode.LOCAL_TOFU),
                RegistryTrustBundle.of(List.of(root.getPublic().getEncoded())),
                RegistrySnapshotPolicy.DEFAULT,
                Clock.systemUTC());
    }

    private static ReliableTlsProcessConfiguration configuration(int port, long serial)
            throws IOException,
                    GeneralSecurityException,
                    OperatorCreationException,
                    TlsTransportException {
        ServerTlsTestCertificateMaterial material = ServerTlsTestCertificateMaterial.create(serial);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        return new ReliableTlsProcessConfiguration(
                new Tls13ServerListenerConfig(
                        new InetSocketAddress("127.0.0.1", port),
                        16,
                        4,
                        4,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2)),
                credentials,
                Duration.ofSeconds(30),
                4,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
