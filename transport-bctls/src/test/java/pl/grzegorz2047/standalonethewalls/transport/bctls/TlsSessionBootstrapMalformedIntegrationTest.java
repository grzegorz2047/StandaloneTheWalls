package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class TlsSessionBootstrapMalformedIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void malformedAcceptRecordsCloseTheLeaseAndDoNotPoisonLaterAdmissions()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    TlsSessionBootstrapException {
        Setup setup = setup();
        TlsSessionBootstrapConfig bootstrapConfig =
                new TlsSessionBootstrapConfig(Duration.ofSeconds(2));
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        BlockingQueue<BootstrappedReliableSession> sessions = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        new Tls13ServerListenerConfig(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                16,
                                2,
                                1,
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(2)),
                        setup.credentials(),
                        connection -> {
                            try {
                                sessions.add(
                                        TlsSessionBootstrap.acceptServerSession(
                                                connection, bootstrapConfig, new SecureRandom()));
                            } catch (Exception exception) {
                                failures.add(exception);
                            }
                        },
                        event -> {});
        listener.start();

        try {
            sendMalformedAccept(
                    listener,
                    setup.trustManager(),
                    offered -> {
                        byte[] record = TlsSessionBootstrapCodec.encodeAccept(offered);
                        record[0] = 0;
                        return record;
                    },
                    TlsSessionBootstrapException.Code.INVALID_MAGIC,
                    failures);
            sendMalformedAccept(
                    listener,
                    setup.trustManager(),
                    TlsSessionBootstrapCodec::encodeOffer,
                    TlsSessionBootstrapException.Code.UNEXPECTED_RECORD_TYPE,
                    failures);
            sendMalformedAccept(
                    listener,
                    setup.trustManager(),
                    offered -> {
                        byte[] record = TlsSessionBootstrapCodec.encodeAccept(offered);
                        ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN).putShort(8, (short) 2);
                        return record;
                    },
                    TlsSessionBootstrapException.Code.UNSUPPORTED_PROTOCOL,
                    failures);
            sendTruncatedAccept(
                    listener,
                    setup.trustManager(),
                    TlsSessionBootstrapException.Code.TRUNCATED_RECORD,
                    failures);

            Tls13Connection validConnection = connectTls(listener, setup.trustManager());
            BootstrappedReliableSession clientSession = null;
            BootstrappedReliableSession serverSession = null;
            try {
                clientSession =
                        TlsSessionBootstrap.connectClientSession(validConnection, bootstrapConfig);
                serverSession = take(sessions, "valid server session");
                assertThat(clientSession.sessionId()).isEqualTo(serverSession.sessionId());
            } finally {
                closeSessionForCleanup(clientSession);
                closeSessionForCleanup(serverSession);
                closeForCleanup(validConnection);
            }
            waitUntil(() -> listener.activeConnectionCount() == 0, "valid session release");
        } finally {
            closeForCleanup(listener);
        }
    }

    private static void sendMalformedAccept(
            Tls13ServerListener listener,
            PinnedServerTrustManager trustManager,
            AcceptRecordFactory recordFactory,
            TlsSessionBootstrapException.Code expected,
            BlockingQueue<Throwable> failures)
            throws IOException,
                    TlsTransportException,
                    InterruptedException,
                    TlsSessionBootstrapException {
        Tls13Connection connection = connectTls(listener, trustManager);
        try {
            UUID offered =
                    TlsSessionBootstrapCodec.decodeOffer(readRecord(connection.inputStream()));
            byte[] record = recordFactory.create(offered);
            connection.outputStream().write(record);
            connection.outputStream().flush();
            requireFailure(take(failures, "malformed accept failure"), expected);
            waitUntil(() -> listener.activeConnectionCount() == 0, "malformed accept release");
        } finally {
            closeForCleanup(connection);
        }
    }

    private static void sendTruncatedAccept(
            Tls13ServerListener listener,
            PinnedServerTrustManager trustManager,
            TlsSessionBootstrapException.Code expected,
            BlockingQueue<Throwable> failures)
            throws IOException,
                    TlsTransportException,
                    InterruptedException,
                    TlsSessionBootstrapException {
        Tls13Connection connection = connectTls(listener, trustManager);
        try {
            UUID offered =
                    TlsSessionBootstrapCodec.decodeOffer(readRecord(connection.inputStream()));
            byte[] accept = TlsSessionBootstrapCodec.encodeAccept(offered);
            connection.outputStream().write(Arrays.copyOf(accept, accept.length - 1));
            connection.outputStream().flush();
        } finally {
            closeForCleanup(connection);
        }
        requireFailure(take(failures, "truncated accept failure"), expected);
        waitUntil(() -> listener.activeConnectionCount() == 0, "truncated accept release");
    }

    private static Setup setup()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, 54L);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore store = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(store);
        trustService.confirmFirstUse(
                REFERENCE, credentials.serverId(), Optional.empty(), "malformed bootstrap test");
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

    private static void requireFailure(
            Throwable failure, TlsSessionBootstrapException.Code expected) {
        if (!(failure instanceof TlsSessionBootstrapException bootstrapFailure)) {
            throw new AssertionError("expected a TLS session bootstrap failure", failure);
        }
        assertThat(bootstrapFailure.code()).isEqualTo(expected);
    }

    private static <T> T take(BlockingQueue<T> queue, String operation)
            throws InterruptedException {
        T value = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (value == null) {
            throw new AssertionError(operation + " timed out");
        }
        return value;
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
            session.closeAsync()
                    .toCompletableFuture()
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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

    @FunctionalInterface
    private interface AcceptRecordFactory {
        byte[] create(UUID offeredSessionId);
    }

    private record Setup(
            Tls13ServerCredentials credentials, PinnedServerTrustManager trustManager) {}
}
