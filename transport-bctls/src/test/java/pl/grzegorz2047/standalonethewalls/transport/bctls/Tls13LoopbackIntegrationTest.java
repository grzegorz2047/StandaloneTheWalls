package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
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
        BouncyCastleTlsContexts contexts = new BouncyCastleTlsContexts();
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        ServerId serverId = ServerId.fromPublicKey(material.keyPair().getPublic().getEncoded());
        trustService.confirmFirstUse(REFERENCE, serverId, Optional.empty(), "loopback test");

        SSLContext serverContext =
                contexts.create(material.keyManagers(), null, new SecureRandom());
        SSLContext clientContext =
                contexts.create(
                        null,
                        new TrustManager[] {
                            new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty())
                        },
                        new SecureRandom());

        try (LoopbackServer server = new LoopbackServer(serverContext)) {
            Future<ServerObservation> observation = server.acceptOne();
            try (SSLSocket client = server.connect(clientContext)) {
                Tls13Policy.configureClient(
                        client, Tls13Policy.ServerAuthentication.PINNED_IDENTITY);
                client.setSoTimeout((int) TIMEOUT.toMillis());
                SecureChannelBinding clientBinding = Tls13Handshake.establish(client);
                Tls13SessionSecurity clientSecurity =
                        Tls13SessionInspector.inspectClient(client, clientBinding);
                client.getOutputStream().write(0x5A);
                client.getOutputStream().flush();
                assertThat(client.getInputStream().read()).isEqualTo(0xA5);

                ServerObservation serverSecurity =
                        observation.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(clientSecurity.serverId()).isEqualTo(serverId);
                assertThat(clientSecurity.channelBinding())
                        .isEqualTo(serverSecurity.channelBinding());
                assertThat(clientSecurity.cipherSuite()).isEqualTo(serverSecurity.cipherSuite());
                assertThat(clientSecurity.applicationProtocol())
                        .isEqualTo(Tls13Policy.APPLICATION_PROTOCOL);
                assertThat(serverSecurity.applicationProtocol())
                        .isEqualTo(Tls13Policy.APPLICATION_PROTOCOL);
                assertThat(serverSecurity.receivedByte()).isEqualTo(0x5A);
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
        BouncyCastleTlsContexts contexts = new BouncyCastleTlsContexts();
        TestCertificateMaterial trusted = TestCertificateMaterial.create(CRYPTO_PROVIDER, 1L);
        TestCertificateMaterial attacker = TestCertificateMaterial.create(CRYPTO_PROVIDER, 2L);
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        trustService.confirmFirstUse(
                REFERENCE,
                ServerId.fromPublicKey(trusted.keyPair().getPublic().getEncoded()),
                Optional.empty(),
                "loopback test");

        SSLContext serverContext =
                contexts.create(attacker.keyManagers(), null, new SecureRandom());
        SSLContext clientContext =
                contexts.create(
                        null,
                        new TrustManager[] {
                            new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty())
                        },
                        new SecureRandom());

        try (LoopbackServer server = new LoopbackServer(serverContext)) {
            Future<ServerObservation> observation = server.acceptOne();
            try (SSLSocket client = server.connect(clientContext)) {
                Tls13Policy.configureClient(
                        client, Tls13Policy.ServerAuthentication.PINNED_IDENTITY);
                client.setSoTimeout((int) TIMEOUT.toMillis());
                Throwable failure = catchThrowable(() -> Tls13Handshake.establish(client));
                assertThat(failure)
                        .isInstanceOf(IOException.class)
                        .hasRootCauseInstanceOf(TlsTrustException.class);
                TlsTrustException trustFailure = findCause(failure, TlsTrustException.class);
                assertThat(trustFailure.status())
                        .isEqualTo(ServerTrustDecision.Status.CHANGED_IDENTITY);
            }
            assertExpectedServerHandshakeFailure(observation);
        }
    }

    @Test
    void rejectsAJsseSocketThatCannotExposeTheTlsExporter() throws IOException {
        try (SSLSocket socket =
                (SSLSocket) javax.net.ssl.SSLSocketFactory.getDefault().createSocket()) {
            assertThatThrownBy(() -> Tls13Handshake.establish(socket))
                    .isInstanceOfSatisfying(
                            TlsTransportException.class,
                            exception ->
                                    assertThat(exception.code())
                                            .isEqualTo(
                                                    TlsTransportException.Code
                                                            .UNSUPPORTED_JSSE_SOCKET));
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

    private record ServerObservation(
            SecureChannelBinding channelBinding,
            String cipherSuite,
            String applicationProtocol,
            int receivedByte) {}

    private static final class LoopbackServer implements AutoCloseable {
        private final SSLServerSocket serverSocket;
        private final ExecutorService executor;

        private LoopbackServer(SSLContext context) throws IOException, TlsTransportException {
            serverSocket =
                    (SSLServerSocket)
                            context.getServerSocketFactory()
                                    .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout((int) TIMEOUT.toMillis());
            Tls13Policy.configureServer(serverSocket);
            executor = Executors.newSingleThreadExecutor();
        }

        private Future<ServerObservation> acceptOne() {
            return executor.submit(
                    () -> {
                        try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                            socket.setSoTimeout((int) TIMEOUT.toMillis());
                            Tls13Policy.configureAcceptedServerSocket(socket);
                            SecureChannelBinding binding = Tls13Handshake.establish(socket);
                            int received = socket.getInputStream().read();
                            socket.getOutputStream().write(0xA5);
                            socket.getOutputStream().flush();
                            return new ServerObservation(
                                    binding,
                                    socket.getSession().getCipherSuite(),
                                    socket.getApplicationProtocol(),
                                    received);
                        }
                    });
        }

        private SSLSocket connect(SSLContext context) throws IOException {
            return (SSLSocket)
                    context.getSocketFactory()
                            .createSocket(
                                    InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
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
