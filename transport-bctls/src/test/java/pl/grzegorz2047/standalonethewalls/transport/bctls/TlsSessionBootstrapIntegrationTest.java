package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class TlsSessionBootstrapIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void agreesOnUuidResetsReadTimeoutAndStartsEnvelopeSequencesAtZero()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    TlsSessionBootstrapException {
        Setup setup = setup(51L);
        TlsSessionBootstrapConfig bootstrapConfig =
                new TlsSessionBootstrapConfig(Duration.ofSeconds(2));
        BlockingQueue<BootstrappedReliableSession> serverSessions = new LinkedBlockingQueue<>();
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> serverTimeouts = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                listener(
                        setup,
                        2,
                        connection -> {
                            try {
                                BootstrappedReliableSession session =
                                        TlsSessionBootstrap.acceptServerSession(
                                                connection, bootstrapConfig, new SecureRandom());
                                serverTimeouts.add(connection.tlsConnection().readTimeoutMillis());
                                serverSessions.add(session);
                            } catch (Exception exception) {
                                failures.add(exception);
                            }
                        });
        listener.start();

        Tls13Connection clientConnection = null;
        BootstrappedReliableSession clientSession = null;
        BootstrappedReliableSession serverSession = null;
        try {
            clientConnection = connectTls(listener, setup.trustManager());
            clientSession =
                    TlsSessionBootstrap.connectClientSession(clientConnection, bootstrapConfig);
            serverSession = take(serverSessions, "server bootstrap result");

            assertThat(failures).isEmpty();
            assertThat(clientSession.sessionId()).isEqualTo(serverSession.sessionId());
            assertThat(clientSession.sessionId().version()).isEqualTo(4);
            assertThat(clientSession.sessionId().variant()).isEqualTo(2);
            assertThat(clientSession.security().serverId())
                    .isEqualTo(serverSession.security().serverId());
            assertThat(clientSession.security().channelBinding())
                    .isEqualTo(serverSession.security().channelBinding());
            assertThat(clientConnection.readTimeoutMillis()).isZero();
            assertThat(take(serverTimeouts, "server reset timeout")).isZero();

            ReliableSendResult clientSend =
                    await(clientSession.reliableChannel().send(MessageType.PING, new byte[] {1}));
            ProtocolEnvelope serverReceived =
                    await(serverSession.reliableChannel().receive()).orElseThrow();
            assertThat(clientSend.sequence()).isZero();
            assertThat(serverReceived.sequence()).isZero();
            assertThat(serverReceived.sessionId()).isEqualTo(clientSession.sessionId());
            assertThat(serverReceived.payload()).containsExactly(1);

            ReliableSendResult serverSend =
                    await(serverSession.reliableChannel().send(MessageType.PONG, new byte[] {2}));
            ProtocolEnvelope clientReceived =
                    await(clientSession.reliableChannel().receive()).orElseThrow();
            assertThat(serverSend.sequence()).isZero();
            assertThat(clientReceived.sequence()).isZero();
            assertThat(clientReceived.sessionId()).isEqualTo(clientSession.sessionId());
            assertThat(clientReceived.payload()).containsExactly(2);

            await(clientSession.closeAsync());
            await(serverSession.closeAsync());
            waitUntil(() -> listener.activeConnectionCount() == 0, "session lease release");
        } finally {
            closeSessionForCleanup(clientSession);
            closeSessionForCleanup(serverSession);
            closeForCleanup(clientConnection);
            closeForCleanup(listener);
        }
    }

    @Test
    void changedAcceptUuidFailsClosedReleasesAdmissionAndAllowsNextSession()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    TlsSessionBootstrapException {
        Setup setup = setup(52L);
        TlsSessionBootstrapConfig bootstrapConfig =
                new TlsSessionBootstrapConfig(Duration.ofSeconds(2));
        BlockingQueue<BootstrappedReliableSession> serverSessions = new LinkedBlockingQueue<>();
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                listener(
                        setup,
                        1,
                        connection -> {
                            try {
                                serverSessions.add(
                                        TlsSessionBootstrap.acceptServerSession(
                                                connection, bootstrapConfig, new SecureRandom()));
                            } catch (Exception exception) {
                                failures.add(exception);
                            }
                        });
        listener.start();

        Tls13Connection maliciousClient = null;
        Tls13Connection validConnection = null;
        BootstrappedReliableSession validClient = null;
        BootstrappedReliableSession validServer = null;
        try {
            maliciousClient = connectTls(listener, setup.trustManager());
            UUID offered =
                    TlsSessionBootstrapCodec.decodeOffer(readRecord(maliciousClient.inputStream()));
            UUID changed =
                    new UUID(
                            offered.getMostSignificantBits(),
                            offered.getLeastSignificantBits() ^ 1L);
            maliciousClient.outputStream().write(TlsSessionBootstrapCodec.encodeAccept(changed));
            maliciousClient.outputStream().flush();

            requireBootstrapFailure(
                    take(failures, "session mismatch failure"),
                    TlsSessionBootstrapException.Code.SESSION_MISMATCH);
            waitUntil(() -> listener.activeConnectionCount() == 0, "mismatch lease release");
            closeForCleanup(maliciousClient);
            maliciousClient = null;

            validConnection = connectTls(listener, setup.trustManager());
            validClient =
                    TlsSessionBootstrap.connectClientSession(validConnection, bootstrapConfig);
            validServer = take(serverSessions, "valid session after mismatch");
            assertThat(validClient.sessionId()).isEqualTo(validServer.sessionId());
        } finally {
            closeSessionForCleanup(validClient);
            closeSessionForCleanup(validServer);
            closeForCleanup(maliciousClient);
            closeForCleanup(validConnection);
            closeForCleanup(listener);
        }
    }

    @Test
    void unansweredOfferTimesOutReleasesAdmissionAndAllowsNextSession()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    TlsSessionBootstrapException {
        Setup setup = setup(53L);
        TlsSessionBootstrapConfig shortConfig =
                new TlsSessionBootstrapConfig(Duration.ofMillis(300));
        BlockingQueue<BootstrappedReliableSession> serverSessions = new LinkedBlockingQueue<>();
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                listener(
                        setup,
                        1,
                        connection -> {
                            try {
                                serverSessions.add(
                                        TlsSessionBootstrap.acceptServerSession(
                                                connection, shortConfig, new SecureRandom()));
                            } catch (Exception exception) {
                                failures.add(exception);
                            }
                        });
        listener.start();

        Tls13Connection silentClient = null;
        Tls13Connection validConnection = null;
        BootstrappedReliableSession validClient = null;
        BootstrappedReliableSession validServer = null;
        try {
            silentClient = connectTls(listener, setup.trustManager());
            requireBootstrapFailure(
                    take(failures, "session bootstrap timeout"),
                    TlsSessionBootstrapException.Code.TIMEOUT);
            waitUntil(() -> listener.activeConnectionCount() == 0, "timeout lease release");
            closeForCleanup(silentClient);
            silentClient = null;

            validConnection = connectTls(listener, setup.trustManager());
            validClient = TlsSessionBootstrap.connectClientSession(validConnection, shortConfig);
            validServer = take(serverSessions, "valid session after timeout");
            assertThat(validClient.sessionId()).isEqualTo(validServer.sessionId());
        } finally {
            closeSessionForCleanup(validClient);
            closeSessionForCleanup(validServer);
            closeForCleanup(silentClient);
            closeForCleanup(validConnection);
            closeForCleanup(listener);
        }
    }

    private static Tls13ServerListener listener(
            Setup setup, int maximumActiveConnections, Tls13AcceptedConnectionHandler handler)
            throws IOException {
        return new Tls13ServerListener(
                new Tls13ServerListenerConfig(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        16,
                        2,
                        maximumActiveConnections,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(2)),
                setup.credentials(),
                handler,
                event -> {});
    }

    private static Setup setup(long serial)
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, serial);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        trustService.confirmFirstUse(
                REFERENCE, credentials.serverId(), Optional.empty(), "bootstrap integration test");
        return new Setup(
                credentials,
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty()));
    }

    private static Tls13Connection connectTls(
            Tls13ServerListener listener, PinnedServerTrustManager trustManager)
            throws IOException, TlsTransportException {
        InetSocketAddress address = listener.localAddress();
        Socket socket = new Socket(address.getAddress(), address.getPort());
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        try {
            return Tls13ClientConnector.connect(socket, trustManager, new SecureRandom());
        } catch (IOException | TlsTransportException | RuntimeException exception) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private static byte[] readRecord(InputStream input) throws IOException {
        byte[] record = new byte[TlsSessionBootstrapCodec.RECORD_BYTES];
        int offset = 0;
        while (offset < record.length) {
            int read = input.read(record, offset, record.length - offset);
            if (read < 0) {
                throw new IOException("session offer ended early");
            }
            if (read == 0) {
                int value = input.read();
                if (value < 0) {
                    throw new IOException("session offer ended early");
                }
                record[offset] = (byte) value;
                offset++;
            } else {
                offset += read;
            }
        }
        return record;
    }

    private static TlsSessionBootstrapException requireBootstrapFailure(
            Throwable failure, TlsSessionBootstrapException.Code expectedCode) {
        if (!(failure instanceof TlsSessionBootstrapException bootstrapFailure)) {
            throw new AssertionError("expected a TLS session bootstrap failure", failure);
        }
        assertThat(bootstrapFailure.code()).isEqualTo(expectedCode);
        return bootstrapFailure;
    }

    private static <T> T take(BlockingQueue<T> queue, String operation)
            throws InterruptedException {
        T result = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (result == null) {
            throw new AssertionError(operation + " timed out");
        }
        return result;
    }

    private static <T> T await(CompletionStage<T> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void waitUntil(BooleanSupplier condition, String operation)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(operation + " timed out");
            }
            Thread.sleep(10L);
        }
    }

    private static void closeSessionForCleanup(BootstrappedReliableSession session) {
        if (session == null) {
            return;
        }
        try {
            await(session.closeAsync());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while closing a bootstrapped session", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("bootstrapped session cleanup failed", exception);
        }
    }

    private static void closeForCleanup(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception exception) {
            throw new AssertionError("test resource cleanup failed", exception);
        }
    }

    private record Setup(
            Tls13ServerCredentials credentials, PinnedServerTrustManager trustManager) {}
}
