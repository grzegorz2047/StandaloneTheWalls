package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannelException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class AsyncTlsReliableChannelIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UUID SESSION_ID = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void asynchronouslyRoundTripsMultipleFramesAndObservesCleanEof()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup();
        try (AsyncLoopbackServer server = new AsyncLoopbackServer(setup.credentials())) {
            Future<List<ProtocolEnvelope>> exchange = server.exchange(SESSION_ID);
            AsyncTlsReliableChannel client = connect(server, setup.trustManager());
            try {
                CompletionStage<ReliableSendResult> ping =
                        client.send(MessageType.PING, new byte[] {1});
                CompletionStage<ReliableSendResult> hello =
                        client.send(MessageType.CLIENT_HELLO, new byte[] {2});
                assertThat(List.of(await(ping).sequence(), await(hello).sequence()))
                        .containsExactlyInAnyOrder(0L, 1L);

                ProtocolEnvelope first = await(client.receive()).orElseThrow();
                ProtocolEnvelope second = await(client.receive()).orElseThrow();
                assertThat(List.of(first.sequence(), second.sequence())).containsExactly(0L, 1L);
                assertThat(List.of(first.messageType(), second.messageType()))
                        .containsExactly(MessageType.PONG, MessageType.SERVER_HELLO);
                assertThat(await(client.receive())).isEmpty();
                await(client.close());

                List<ProtocolEnvelope> received =
                        exchange.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(received).extracting(ProtocolEnvelope::sequence).containsExactly(0L, 1L);
                assertThat(received)
                        .extracting(ProtocolEnvelope::messageType)
                        .containsExactlyInAnyOrder(MessageType.PING, MessageType.CLIENT_HELLO);
            } finally {
                settle(client.close());
            }
        }
    }

    @Test
    void closeCompletesABlockedReceiveWithoutBlockingTheCaller()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup();
        try (AsyncLoopbackServer server = new AsyncLoopbackServer(setup.credentials())) {
            Future<Integer> peerRead = server.waitForPeerClose();
            AsyncTlsReliableChannel client = connect(server, setup.trustManager());
            CompletionStage<Optional<ProtocolEnvelope>> blocked = client.receive();

            long started = System.nanoTime();
            CompletionStage<Void> closed = client.close();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).isLessThan(100L);
            ReliableChannelException failure =
                    channelFailure(blocked, ReliableChannelException.Code.CLOSED);
            assertThat(failure.getMessage()).doesNotContain("payload");
            await(closed);
            assertThat(peerRead.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(-1);
        }
    }

    @Test
    void malformedFrameFailureClosesTheAsyncChannelAndIsPreserved()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup();
        byte[] invalid =
                ProtocolCodec.encode(
                        new ProtocolEnvelope(
                                ProtocolVersion.CURRENT,
                                MessageType.PING,
                                SESSION_ID,
                                0L,
                                new byte[] {1}));
        invalid[0] = 0;

        try (AsyncLoopbackServer server = new AsyncLoopbackServer(setup.credentials())) {
            Future<Void> sender = server.sendRaw(invalid);
            AsyncTlsReliableChannel client = connect(server, setup.trustManager());

            Throwable receiveFailure = failure(client.receive());
            assertThat(receiveFailure).isInstanceOf(ProtocolException.class);
            assertThat(((ProtocolException) receiveFailure).code())
                    .isEqualTo(ProtocolException.Code.INVALID_MAGIC);
            assertThat(failure(client.close())).isSameAs(receiveFailure);
            assertThat(client.isOpen()).isFalse();
            sender.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static AsyncTlsReliableChannel connect(
            AsyncLoopbackServer server, PinnedServerTrustManager trustManager)
            throws IOException, TlsTransportException {
        Tls13Connection connection =
                Tls13ClientConnector.connect(
                        server.connectSocket(), trustManager, new SecureRandom());
        return new AsyncTlsReliableChannel(new TlsEnvelopeStream(connection, SESSION_ID));
    }

    private static Setup setup()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 21L);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        trustService.confirmFirstUse(
                REFERENCE, credentials.serverId(), Optional.empty(), "async loopback test");
        return new Setup(
                credentials,
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty()));
    }

    private static ReliableChannelException channelFailure(
            CompletionStage<?> stage, ReliableChannelException.Code expectedCode)
            throws InterruptedException, TimeoutException {
        Throwable failure = failure(stage);
        assertThat(failure).isInstanceOf(ReliableChannelException.class);
        ReliableChannelException channelFailure = (ReliableChannelException) failure;
        assertThat(channelFailure.code()).isEqualTo(expectedCode);
        return channelFailure;
    }

    private static Throwable failure(CompletionStage<?> stage)
            throws InterruptedException, TimeoutException {
        try {
            await(stage);
            throw new AssertionError("expected the stage to fail");
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    private static void settle(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // The assertion path already reports the primary failure.
        }
    }

    private static <T> T await(CompletionStage<T> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private record Setup(
            Tls13ServerCredentials credentials, PinnedServerTrustManager trustManager) {}

    private static final class AsyncLoopbackServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Tls13ServerCredentials credentials;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        private AsyncLoopbackServer(Tls13ServerCredentials credentials) throws IOException {
            this.credentials = credentials;
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout((int) TIMEOUT.toMillis());
        }

        private Future<List<ProtocolEnvelope>> exchange(UUID sessionId) {
            return executor.submit(
                    () -> {
                        AsyncTlsReliableChannel channel = acceptChannel(sessionId);
                        try {
                            ProtocolEnvelope first = await(channel.receive()).orElseThrow();
                            ProtocolEnvelope second = await(channel.receive()).orElseThrow();
                            assertThat(
                                            await(channel.send(MessageType.PONG, new byte[] {9}))
                                                    .sequence())
                                    .isZero();
                            assertThat(
                                            await(
                                                            channel.send(
                                                                    MessageType.SERVER_HELLO,
                                                                    new byte[] {8}))
                                                    .sequence())
                                    .isEqualTo(1L);
                            await(channel.close());
                            return List.of(first, second);
                        } finally {
                            settle(channel.close());
                        }
                    });
        }

        private Future<Integer> waitForPeerClose() {
            return executor.submit(
                    () -> {
                        Tls13Connection connection = acceptConnection();
                        try (connection) {
                            return connection.inputStream().read();
                        }
                    });
        }

        private Future<Void> sendRaw(byte[] encoded) {
            return executor.submit(
                    () -> {
                        Tls13Connection connection = acceptConnection();
                        try (connection) {
                            connection.outputStream().write(encoded);
                            connection.outputStream().flush();
                        }
                        return null;
                    });
        }

        private AsyncTlsReliableChannel acceptChannel(UUID sessionId)
                throws IOException, TlsTransportException {
            return new AsyncTlsReliableChannel(
                    new TlsEnvelopeStream(acceptConnection(), sessionId));
        }

        private Tls13Connection acceptConnection() throws IOException, TlsTransportException {
            Socket socket = serverSocket.accept();
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            return Tls13ServerAcceptor.accept(socket, credentials, new SecureRandom());
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
                    throw new IOException("async loopback server executor did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "interrupted while stopping async loopback server", exception);
            }
        }
    }
}
