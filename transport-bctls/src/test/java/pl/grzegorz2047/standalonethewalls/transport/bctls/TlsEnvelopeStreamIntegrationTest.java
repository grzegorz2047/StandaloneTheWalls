package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class TlsEnvelopeStreamIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();
    private static final UUID SESSION_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void roundTripsMultipleEnvelopesWithConcurrentWriters()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    ProtocolException {
        Setup setup = setup();
        try (LoopbackEnvelopeServer server = new LoopbackEnvelopeServer(setup.credentials())) {
            Future<List<ProtocolEnvelope>> serverExchange = server.acceptExchange(SESSION_ID);
            Tls13Connection connection =
                    Tls13ClientConnector.connect(
                            server.connectSocket(), setup.trustManager(), new SecureRandom());
            try (TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, SESSION_ID)) {
                ExecutorService writers = Executors.newFixedThreadPool(2);
                Sent first;
                Sent second;
                try {
                    Future<Sent> ping =
                            writers.submit(
                                    () ->
                                            new Sent(
                                                    MessageType.PING,
                                                    stream.send(
                                                            MessageType.PING,
                                                            new byte[] {1})));
                    Future<Sent> hello =
                            writers.submit(
                                    () ->
                                            new Sent(
                                                    MessageType.CLIENT_HELLO,
                                                    stream.send(
                                                            MessageType.CLIENT_HELLO,
                                                            new byte[] {2})));
                    first = ping.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    second = hello.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } finally {
                    stopExecutor(writers);
                }

                assertThat(List.of(first.sequence(), second.sequence()))
                        .containsExactlyInAnyOrder(0L, 1L);
                assertThat(List.of(first.messageType(), second.messageType()))
                        .containsExactlyInAnyOrder(MessageType.PING, MessageType.CLIENT_HELLO);

                ProtocolEnvelope responseZero = stream.receive().orElseThrow();
                ProtocolEnvelope responseOne = stream.receive().orElseThrow();
                assertThat(responseZero.sequence()).isZero();
                assertThat(responseZero.messageType()).isEqualTo(MessageType.PONG);
                assertThat(responseZero.payload()).containsExactly(9);
                assertThat(responseOne.sequence()).isEqualTo(1L);
                assertThat(responseOne.messageType()).isEqualTo(MessageType.SERVER_HELLO);
                assertThat(responseOne.payload()).containsExactly(8);

                List<ProtocolEnvelope> received =
                        serverExchange.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(received).extracting(ProtocolEnvelope::sequence).containsExactly(0L, 1L);
                assertThat(received)
                        .extracting(ProtocolEnvelope::messageType)
                        .containsExactlyInAnyOrder(MessageType.PING, MessageType.CLIENT_HELLO);
            }
        }
    }

    @Test
    void closesOnDuplicateInboundSequence()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    ProtocolException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup();
        byte[] first = encoded(SESSION_ID, MessageType.PING, 0L, new byte[] {1});
        byte[] duplicate = encoded(SESSION_ID, MessageType.PONG, 0L, new byte[] {2});

        try (LoopbackEnvelopeServer server = new LoopbackEnvelopeServer(setup.credentials())) {
            Future<Void> sender = server.sendRaw(concatenate(first, duplicate));
            Tls13Connection connection =
                    Tls13ClientConnector.connect(
                            server.connectSocket(), setup.trustManager(), new SecureRandom());
            try (TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, SESSION_ID)) {
                assertThat(stream.receive().orElseThrow().sequence()).isZero();
                assertThatThrownBy(stream::receive)
                        .isInstanceOfSatisfying(
                                ProtocolException.class,
                                exception ->
                                        assertThat(exception.code())
                                                .isEqualTo(
                                                        ProtocolException.Code
                                                                .OUT_OF_ORDER_SEQUENCE));
                assertThat(stream.isOpen()).isFalse();
            }
            sender.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void closesOnForeignSessionAndTruncatedPayload()
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
        UUID foreignSession = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertReceiveFailure(
                setup,
                encoded(foreignSession, MessageType.PING, 0L, new byte[] {1}),
                ProtocolException.Code.SESSION_MISMATCH);

        byte[] complete = encoded(SESSION_ID, MessageType.PING, 0L, new byte[] {1, 2});
        assertReceiveFailure(
                setup,
                Arrays.copyOf(complete, complete.length - 1),
                ProtocolException.Code.TRUNCATED_MESSAGE);
    }

    @Test
    void cleanEofBeforeANewHeaderEndsTheStream()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    ProtocolException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup();
        try (LoopbackEnvelopeServer server = new LoopbackEnvelopeServer(setup.credentials())) {
            Future<Void> sender = server.sendRaw(new byte[0]);
            Tls13Connection connection =
                    Tls13ClientConnector.connect(
                            server.connectSocket(), setup.trustManager(), new SecureRandom());
            try (TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, SESSION_ID)) {
                assertThat(stream.receive()).isEmpty();
                assertThat(stream.isOpen()).isFalse();
            }
            sender.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static void assertReceiveFailure(
            Setup setup, byte[] encoded, ProtocolException.Code expectedCode)
            throws IOException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        try (LoopbackEnvelopeServer server = new LoopbackEnvelopeServer(setup.credentials())) {
            Future<Void> sender = server.sendRaw(encoded);
            Tls13Connection connection =
                    Tls13ClientConnector.connect(
                            server.connectSocket(), setup.trustManager(), new SecureRandom());
            try (TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, SESSION_ID)) {
                assertThatThrownBy(stream::receive)
                        .isInstanceOfSatisfying(
                                ProtocolException.class,
                                exception -> assertThat(exception.code()).isEqualTo(expectedCode));
                assertThat(stream.isOpen()).isFalse();
            }
            sender.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static Setup setup()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 11L);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        trustService.confirmFirstUse(
                REFERENCE, credentials.serverId(), Optional.empty(), "envelope loopback test");
        return new Setup(
                credentials,
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty()));
    }

    private static byte[] encoded(
            UUID sessionId, MessageType messageType, long sequence, byte[] payload) {
        return ProtocolCodec.encode(
                new ProtocolEnvelope(
                        ProtocolVersion.CURRENT, messageType, sessionId, sequence, payload));
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static void stopExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("test writer executor did not terminate");
        }
    }

    private record Setup(
            Tls13ServerCredentials credentials, PinnedServerTrustManager trustManager) {}

    private record Sent(MessageType messageType, long sequence) {}

    private static final class LoopbackEnvelopeServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Tls13ServerCredentials credentials;
        private final ExecutorService executor;

        private LoopbackEnvelopeServer(Tls13ServerCredentials credentials) throws IOException {
            this.credentials = credentials;
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout((int) TIMEOUT.toMillis());
            executor = Executors.newSingleThreadExecutor();
        }

        private Future<List<ProtocolEnvelope>> acceptExchange(UUID sessionId) {
            return executor.submit(
                    () -> {
                        Socket socket = acceptSocket();
                        Tls13Connection connection =
                                Tls13ServerAcceptor.accept(
                                        socket, credentials, new SecureRandom());
                        try (TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, sessionId)) {
                            ProtocolEnvelope first = stream.receive().orElseThrow();
                            ProtocolEnvelope second = stream.receive().orElseThrow();
                            assertThat(stream.send(MessageType.PONG, new byte[] {9})).isZero();
                            assertThat(stream.send(MessageType.SERVER_HELLO, new byte[] {8}))
                                    .isEqualTo(1L);
                            return List.of(first, second);
                        }
                    });
        }

        private Future<Void> sendRaw(byte[] encoded) {
            return executor.submit(
                    () -> {
                        Socket socket = acceptSocket();
                        try (Tls13Connection connection =
                                Tls13ServerAcceptor.accept(
                                        socket, credentials, new SecureRandom())) {
                            connection.outputStream().write(encoded);
                            connection.outputStream().flush();
                        }
                        return null;
                    });
        }

        private Socket acceptSocket() throws IOException {
            Socket socket = serverSocket.accept();
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            return socket;
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
                    throw new IOException("loopback envelope executor did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "interrupted while stopping loopback envelope executor", exception);
            }
        }
    }
}
