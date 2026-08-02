package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class Tls13ServerListenerIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();

    @Test
    void enforcesActiveLimitReusesAdmissionAndClosesAnActiveLeaseOnShutdown()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup(31L);
        BlockingQueue<AcceptedTlsConnection> accepted = new LinkedBlockingQueue<>();
        BlockingQueue<Tls13ServerListenerEvent> events = new LinkedBlockingQueue<>();
        AtomicReference<Thread> handlerThread = new AtomicReference<>();
        Tls13ServerListenerConfig config = config(2, 1, Duration.ofSeconds(3));
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        config,
                        setup.credentials(),
                        connection -> {
                            handlerThread.set(Thread.currentThread());
                            accepted.add(connection);
                        },
                        events::add);
        listener.start();

        Tls13Connection firstClient = null;
        Tls13Connection thirdClient = null;
        Socket rejected = null;
        try {
            firstClient = connect(listener, setup.trustManager());
            AcceptedTlsConnection firstLease = take(accepted, "first accepted connection");
            assertThat(firstLease.connectionId()).isPositive();
            assertThat(firstLease.security().serverId()).isEqualTo(setup.credentials().serverId());
            assertThat(handlerThread.get()).isNotNull();
            assertThat(handlerThread.get().isVirtual()).isTrue();
            assertThat(handlerThread.get().getName()).contains("-handshake-");
            assertThat(listener.activeConnectionCount()).isEqualTo(1);

            rejected = connectRaw(listener);
            assertThat(rejected.getInputStream().read()).isEqualTo(-1);
            assertThat(
                            awaitEvent(
                                            events,
                                            Tls13ServerListenerEvent.Code.ACTIVE_CONNECTION_LIMIT)
                                    .remoteAddress())
                    .isPresent();
            assertThat(listener.activeConnectionCount()).isEqualTo(1);

            firstLease.close();
            firstLease.close();
            waitUntil(() -> listener.activeConnectionCount() == 0, "first lease release");
            closeForCleanup(firstClient);
            firstClient = null;

            thirdClient = connect(listener, setup.trustManager());
            AcceptedTlsConnection thirdLease = take(accepted, "third accepted connection");
            assertThat(thirdLease.connectionId()).isGreaterThan(firstLease.connectionId());
            assertThat(listener.activeConnectionCount()).isEqualTo(1);

            await(listener.closeAsync());
            assertThat(listener.isTerminated()).isTrue();
            assertThat(listener.failure()).isEmpty();
            assertThat(listener.activeConnectionCount()).isZero();
            assertThat(listener.inFlightHandshakeCount()).isZero();
            assertThat(thirdLease.isOpen()).isFalse();
        } finally {
            closeForCleanup(rejected);
            closeForCleanup(firstClient);
            closeForCleanup(thirdClient);
            closeForCleanup(listener);
        }
    }

    @Test
    void handshakeLimitAndTimeoutReleaseAdmissionForANextValidClient()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup(32L);
        BlockingQueue<AcceptedTlsConnection> accepted = new LinkedBlockingQueue<>();
        BlockingQueue<Tls13ServerListenerEvent> events = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        config(1, 2, Duration.ofSeconds(2)),
                        setup.credentials(),
                        accepted::add,
                        events::add);
        listener.start();

        Socket stalled = null;
        Socket rejected = null;
        Tls13Connection validClient = null;
        try {
            stalled = connectRaw(listener);
            waitUntil(() -> listener.inFlightHandshakeCount() == 1, "stalled handshake admission");
            assertThat(listener.activeConnectionCount()).isZero();

            rejected = connectRaw(listener);
            assertThat(rejected.getInputStream().read()).isEqualTo(-1);
            awaitEvent(events, Tls13ServerListenerEvent.Code.CONCURRENT_HANDSHAKE_LIMIT);

            Tls13ServerListenerEvent timeoutEvent =
                    awaitEvent(events, Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED);
            assertThat(timeoutEvent.failure()).isPresent();
            waitUntil(() -> listener.inFlightHandshakeCount() == 0, "handshake timeout release");

            validClient = connect(listener, setup.trustManager());
            AcceptedTlsConnection lease = take(accepted, "valid connection after timeout");
            assertThat(lease.security().serverId()).isEqualTo(setup.credentials().serverId());
            lease.close();
            waitUntil(() -> listener.activeConnectionCount() == 0, "valid lease release");
            assertThat(listener.isRunning()).isTrue();
        } finally {
            closeForCleanup(stalled);
            closeForCleanup(rejected);
            closeForCleanup(validClient);
            closeForCleanup(listener);
        }
    }

    @Test
    void handlerFailureClosesItsLeaseWithoutStoppingLaterAdmissions()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup(33L);
        BlockingQueue<AcceptedTlsConnection> accepted = new LinkedBlockingQueue<>();
        BlockingQueue<Tls13ServerListenerEvent> events = new LinkedBlockingQueue<>();
        AtomicInteger calls = new AtomicInteger();
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        config(2, 1, Duration.ofSeconds(3)),
                        setup.credentials(),
                        connection -> {
                            if (calls.incrementAndGet() == 1) {
                                throw new IllegalStateException("handler fixture failure");
                            }
                            accepted.add(connection);
                        },
                        events::add);
        listener.start();

        Tls13Connection firstClient = null;
        Tls13Connection secondClient = null;
        try {
            firstClient = connect(listener, setup.trustManager());
            Tls13ServerListenerEvent handlerFailure =
                    awaitEvent(events, Tls13ServerListenerEvent.Code.HANDLER_FAILED);
            assertThat(handlerFailure.failure())
                    .hasValueSatisfying(
                            failure ->
                                    assertThat(failure).isInstanceOf(IllegalStateException.class));
            waitUntil(() -> listener.activeConnectionCount() == 0, "failed handler lease release");
            closeForCleanup(firstClient);
            firstClient = null;

            secondClient = connect(listener, setup.trustManager());
            AcceptedTlsConnection secondLease = take(accepted, "second accepted connection");
            assertThat(secondLease.isOpen()).isTrue();
            assertThat(calls.get()).isEqualTo(2);
            assertThat(listener.isRunning()).isTrue();
            secondLease.close();
        } finally {
            closeForCleanup(firstClient);
            closeForCleanup(secondClient);
            closeForCleanup(listener);
        }
    }

    @Test
    void shutdownClosesAStalledHandshakeAndOwnedThreads()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    TlsTransportException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup;
        try {
            setup = setup(34L);
        } catch (IdentityException | ServerTrustStoreException exception) {
            throw new AssertionError("listener test identity setup failed", exception);
        }
        BlockingQueue<Tls13ServerListenerEvent> events = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        config(1, 1, Duration.ofSeconds(10)),
                        setup.credentials(),
                        connection -> {
                            try {
                                connection.close();
                            } catch (IOException exception) {
                                throw new IllegalStateException(
                                        "listener fixture could not close a connection", exception);
                            }
                        },
                        events::add);
        listener.start();

        Socket stalled = null;
        try {
            stalled = connectRaw(listener);
            waitUntil(() -> listener.inFlightHandshakeCount() == 1, "stalled shutdown handshake");
            long started = System.nanoTime();
            await(listener.closeAsync());
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).isLessThan(5_000L);
            assertThat(listener.isTerminated()).isTrue();
            assertThat(listener.failure()).isEmpty();
            assertThat(listener.inFlightHandshakeCount()).isZero();
            assertThat(listener.activeConnectionCount()).isZero();
            assertThat(stalled.getInputStream().read()).isEqualTo(-1);
            assertThat(events)
                    .noneMatch(
                            event ->
                                    event.code()
                                            == Tls13ServerListenerEvent.Code.ACCEPT_LOOP_FAILED);
        } finally {
            closeForCleanup(stalled);
            closeForCleanup(listener);
        }
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
                REFERENCE, credentials.serverId(), Optional.empty(), "listener integration test");
        return new Setup(
                credentials,
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty()));
    }

    private static Tls13ServerListenerConfig config(
            int maximumConcurrentHandshakes,
            int maximumActiveConnections,
            Duration handshakeTimeout) {
        return new Tls13ServerListenerConfig(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                16,
                maximumConcurrentHandshakes,
                maximumActiveConnections,
                handshakeTimeout,
                Duration.ofSeconds(2));
    }

    private static Tls13Connection connect(
            Tls13ServerListener listener, PinnedServerTrustManager trustManager)
            throws IOException, TlsTransportException {
        Socket socket = connectRaw(listener);
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

    private static Socket connectRaw(Tls13ServerListener listener) throws IOException {
        InetSocketAddress address = listener.localAddress();
        Socket socket = new Socket(address.getAddress(), address.getPort());
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    private static AcceptedTlsConnection take(
            BlockingQueue<AcceptedTlsConnection> queue, String operation)
            throws InterruptedException {
        AcceptedTlsConnection connection = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (connection == null) {
            throw new AssertionError(operation + " timed out");
        }
        return connection;
    }

    private static Tls13ServerListenerEvent awaitEvent(
            BlockingQueue<Tls13ServerListenerEvent> events, Tls13ServerListenerEvent.Code expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new AssertionError("listener event " + expected + " timed out");
            }
            Tls13ServerListenerEvent event = events.poll(remaining, TimeUnit.NANOSECONDS);
            if (event == null) {
                throw new AssertionError("listener event " + expected + " timed out");
            }
            if (event.code() == expected) {
                return event;
            }
        }
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

    private static void await(CompletionStage<Void> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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
