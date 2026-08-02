package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class Tls13LoopbackIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void negotiatesTls13AlpnAndTheSameExporterOnBothPeers()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        Tls13ServerCredentials serverCredentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        ServerId serverId = serverCredentials.serverId();
        trustService.confirmFirstUse(REFERENCE, serverId, Optional.empty(), "loopback test");
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty());

        try (LoopbackServer server = new LoopbackServer(serverCredentials)) {
            Future<ServerObservation> observation = server.acceptOne();
            try (Tls13Connection client =
                    Tls13ClientConnector.connect(
                            server.connectSocket(), trustManager, new SecureRandom())) {
                client.outputStream().write(0x5A);
                client.outputStream().flush();
                assertThat(client.inputStream().read()).isEqualTo(0xA5);

                ServerObservation serverObservation =
                        observation.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(client.security().serverId()).isEqualTo(serverId);
                assertThat(client.security().channelBinding())
                        .isEqualTo(serverObservation.security().channelBinding());
                assertThat(client.security().cipherSuite())
                        .isEqualTo(serverObservation.security().cipherSuite());
                assertThat(client.security().applicationProtocol())
                        .isEqualTo(Tls13ProtocolPolicy.APPLICATION_PROTOCOL);
                assertThat(serverObservation.security().applicationProtocol())
                        .isEqualTo(Tls13ProtocolPolicy.APPLICATION_PROTOCOL);
                assertThat(serverObservation.receivedByte()).isEqualTo(0x5A);
            }
        }
    }

    @Test
    void changedPinnedIdentityAbortsTheTlsHandshake()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial trusted = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        TestCertificateMaterial attacker = TestCertificateMaterial.create(CRYPTO_PROVIDER, 2L);
        Tls13ServerCredentials attackerCredentials =
                Tls13ServerCredentials.create(
                        attacker.keyPair().getPrivate(), List.of(attacker.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        ServerId trustedId = ServerId.fromPublicKey(trusted.keyPair().getPublic().getEncoded());
        trustService.confirmFirstUse(
                REFERENCE, trustedId, Optional.empty(), "loopback test");
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty());

        try (LoopbackServer server = new LoopbackServer(attackerCredentials)) {
            Future<ServerObservation> observation = server.acceptOne();
            Throwable failure =
                    catchThrowable(
                            () ->
                                    Tls13ClientConnector.connect(
                                            server.connectSocket(),
                                            trustManager,
                                            new SecureRandom()));
            assertThat(failure)
                    .isInstanceOf(IOException.class)
                    .hasRootCauseInstanceOf(TlsTrustException.class);
            TlsTrustException trustFailure = findCause(failure, TlsTrustException.class);
            assertThat(trustFailure.status())
                    .isEqualTo(ServerTrustDecision.Status.CHANGED_IDENTITY);
            assertThat(store.find(REFERENCE).orElseThrow().serverId()).isEqualTo(trustedId);
            assertExpectedServerHandshakeFailure(observation);
        }
    }

    @Test
    void requiresAConnectedSocketWithABoundedReadTimeout() throws IOException {
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(
                        new ServerTrustService(store), REFERENCE, Optional.empty());

        try (Socket socket = new Socket()) {
            assertThatThrownBy(
                            () ->
                                    Tls13ClientConnector.connect(
                                            socket, trustManager, new SecureRandom()))
                    .isInstanceOfSatisfying(
                            TlsTransportException.class,
                            exception ->
                                    assertThat(exception.code())
                                            .isEqualTo(
                                                    TlsTransportException.Code
                                                            .SOCKET_CONFIGURATION_INVALID));
        }

        try (ServerSocket listener =
                        new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket socket =
                        new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            assertThat(socket.getSoTimeout()).isZero();
            assertThatThrownBy(
                            () ->
                                    Tls13ClientConnector.connect(
                                            socket, trustManager, new SecureRandom()))
                    .isInstanceOfSatisfying(
                            TlsTransportException.class,
                            exception ->
                                    assertThat(exception.code())
                                            .isEqualTo(
                                                    TlsTransportException.Code
                                                            .SOCKET_CONFIGURATION_INVALID));
        }
    }

    private static void assertExpectedServerHandshakeFailure(
            Future<ServerObservation> observation) {
        assertThatThrownBy(() -> observation.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        throw new AssertionError("expected cause " + type.getName(), failure);
    }

    private record ServerObservation(Tls13SessionSecurity security, int receivedByte) {}

    private static final class LoopbackServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Tls13ServerCredentials credentials;
        private final ExecutorService executor;

        private LoopbackServer(Tls13ServerCredentials credentials) throws IOException {
            this.credentials = credentials;
            serverSocket =
                    new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout((int) TIMEOUT.toMillis());
            executor = Executors.newSingleThreadExecutor();
        }

        private Future<ServerObservation> acceptOne() {
            return executor.submit(
                    () -> {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout((int) TIMEOUT.toMillis());
                        try (Tls13Connection connection =
                                Tls13ServerAcceptor.accept(
                                        socket, credentials, new SecureRandom())) {
                            int received = connection.inputStream().read();
                            connection.outputStream().write(0xA5);
                            connection.outputStream().flush();
                            return new ServerObservation(connection.security(), received);
                        }
                    });
        }

        private Socket connectSocket() throws IOException {
            Socket socket =
                    new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            return socket;
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("loopback TLS executor did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "interrupted while stopping loopback TLS executor", exception);
            }
        }
    }
}
