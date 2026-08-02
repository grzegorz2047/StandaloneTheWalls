package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns one bounded TLS server endpoint and delivers authenticated connection leases.
 *
 * <p>The accept loop uses one named platform thread. TLS handshakes use owned named virtual
 * threads. No operation runs on the caller or fixed-tick simulation thread.
 */
public final class Tls13ServerListener implements AutoCloseable {
    private static final AtomicLong LISTENER_IDS = new AtomicLong();

    private final Tls13ServerListenerConfig config;
    private final Tls13ServerCredentials credentials;
    private final Tls13AcceptedConnectionHandler handler;
    private final Consumer<Tls13ServerListenerEvent> eventObserver;
    private final Supplier<SecureRandom> secureRandomSupplier;
    private final ServerSocket serverSocket;
    private final ExecutorService handshakeExecutor;
    private final ThreadFactory acceptThreadFactory;
    private final ThreadFactory closeThreadFactory;
    private final Semaphore handshakePermits;
    private final Semaphore activeConnectionPermits;
    private final Set<Socket> handshakeSockets = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, AcceptedTlsConnection> activeConnections =
            new ConcurrentHashMap<>();
    private final AtomicLong nextConnectionId = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final AtomicReference<Throwable> terminalFailure = new AtomicReference<>();
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    private final Object lifecycleLock = new Object();

    private volatile Thread acceptThread;

    public Tls13ServerListener(
            Tls13ServerListenerConfig config,
            Tls13ServerCredentials credentials,
            Tls13AcceptedConnectionHandler handler,
            Consumer<Tls13ServerListenerEvent> eventObserver)
            throws IOException {
        this(config, credentials, handler, eventObserver, SecureRandom::new);
    }

    Tls13ServerListener(
            Tls13ServerListenerConfig config,
            Tls13ServerCredentials credentials,
            Tls13AcceptedConnectionHandler handler,
            Consumer<Tls13ServerListenerEvent> eventObserver,
            Supplier<SecureRandom> secureRandomSupplier)
            throws IOException {
        this.config = Objects.requireNonNull(config, "config");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
        this.secureRandomSupplier =
                Objects.requireNonNull(secureRandomSupplier, "secureRandomSupplier");

        long listenerId = LISTENER_IDS.incrementAndGet();
        String prefix = "sunderfront-tls-listener-" + listenerId;
        this.handshakeExecutor =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name(prefix + "-handshake-", 0L).factory());
        this.acceptThreadFactory =
                Thread.ofPlatform().name(prefix + "-accept").daemon(false).factory();
        this.closeThreadFactory = Thread.ofVirtual().name(prefix + "-close").factory();
        this.handshakePermits = new Semaphore(config.maximumConcurrentHandshakes(), true);
        this.activeConnectionPermits = new Semaphore(config.maximumActiveConnections(), true);

        ServerSocket socket = new ServerSocket();
        boolean bound = false;
        try {
            socket.setReuseAddress(true);
            socket.bind(config.bindAddress(), config.backlog());
            bound = true;
        } finally {
            if (!bound) {
                socket.close();
                handshakeExecutor.shutdownNow();
            }
        }
        this.serverSocket = socket;
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (!state.compareAndSet(State.NEW, State.RUNNING)) {
                throw new IllegalStateException("TLS listener can be started only once");
            }
            Thread thread = acceptThreadFactory.newThread(this::runAcceptLoop);
            acceptThread = thread;
            try {
                thread.start();
            } catch (RuntimeException exception) {
                initiateClose(exception);
                throw exception;
            }
        }
    }

    public boolean isRunning() {
        return state.get() == State.RUNNING;
    }

    public boolean isTerminated() {
        State current = state.get();
        return current == State.CLOSED || current == State.FAILED;
    }

    public int inFlightHandshakeCount() {
        return handshakeSockets.size();
    }

    public int activeConnectionCount() {
        return activeConnections.size();
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(terminalFailure.get());
    }

    public CompletionStage<Void> closeAsync() {
        initiateClose(null);
        return closeFuture.minimalCompletionStage();
    }

    @Override
    public void close() throws IOException {
        CompletionStage<Void> closing = closeAsync();
        Duration wait = config.shutdownTimeout().multipliedBy(3L).plusSeconds(1L);
        try {
            closing.toCompletableFuture().get(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while closing the TLS listener", exception);
        } catch (TimeoutException exception) {
            throw new IOException(
                    "TLS listener close did not complete within its bounded wait", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("TLS listener close failed", cause);
        }
    }

    private void runAcceptLoop() {
        try {
            while (state.get() == State.RUNNING) {
                Socket socket = serverSocket.accept();
                if (state.get() != State.RUNNING) {
                    closeDuringShutdown(socket);
                    break;
                }
                admit(socket);
            }
        } catch (SocketException exception) {
            if (state.get() == State.RUNNING) {
                publish(
                        Tls13ServerListenerEvent.failed(
                                Tls13ServerListenerEvent.Code.ACCEPT_LOOP_FAILED, null, exception));
                initiateClose(exception);
            }
        } catch (IOException | RuntimeException exception) {
            if (state.get() == State.RUNNING) {
                publish(
                        Tls13ServerListenerEvent.failed(
                                Tls13ServerListenerEvent.Code.ACCEPT_LOOP_FAILED, null, exception));
                initiateClose(exception);
            }
        }
    }

    private void admit(Socket socket) {
        SocketAddress remoteAddress = socket.getRemoteSocketAddress();
        try {
            socket.setSoTimeout(config.handshakeTimeoutMillis());
            socket.setTcpNoDelay(true);
        } catch (SocketException exception) {
            closeWithSuppressed(socket, exception);
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED,
                            remoteAddress,
                            exception));
            return;
        }

        if (!activeConnectionPermits.tryAcquire()) {
            closeRejectedSocket(socket, remoteAddress);
            publish(
                    Tls13ServerListenerEvent.rejected(
                            Tls13ServerListenerEvent.Code.ACTIVE_CONNECTION_LIMIT, remoteAddress));
            return;
        }
        if (!handshakePermits.tryAcquire()) {
            activeConnectionPermits.release();
            closeRejectedSocket(socket, remoteAddress);
            publish(
                    Tls13ServerListenerEvent.rejected(
                            Tls13ServerListenerEvent.Code.CONCURRENT_HANDSHAKE_LIMIT,
                            remoteAddress));
            return;
        }

        handshakeSockets.add(socket);
        try {
            handshakeExecutor.execute(() -> runHandshake(socket, remoteAddress));
        } catch (RejectedExecutionException exception) {
            handshakeSockets.remove(socket);
            handshakePermits.release();
            activeConnectionPermits.release();
            closeWithSuppressed(socket, exception);
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.HANDSHAKE_EXECUTOR_REJECTED,
                            remoteAddress,
                            exception));
            if (state.get() == State.RUNNING) {
                initiateClose(exception);
            }
        }
    }

    private void runHandshake(Socket socket, SocketAddress remoteAddress) {
        Tls13Connection connection;
        try {
            SecureRandom secureRandom =
                    Objects.requireNonNull(
                            secureRandomSupplier.get(), "secureRandomSupplier returned null");
            connection = Tls13ServerAcceptor.accept(socket, credentials, secureRandom);
        } catch (IOException | TlsTransportException | RuntimeException exception) {
            closeWithSuppressed(socket, exception);
            activeConnectionPermits.release();
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED,
                            remoteAddress,
                            exception));
            return;
        } finally {
            handshakeSockets.remove(socket);
            handshakePermits.release();
        }

        long connectionId = nextConnectionId.incrementAndGet();
        AcceptedTlsConnection accepted =
                new AcceptedTlsConnection(
                        connectionId,
                        remoteAddress,
                        connection,
                        () -> releaseActiveConnection(connectionId));
        activeConnections.put(connectionId, accepted);
        if (state.get() != State.RUNNING) {
            closeDuringShutdown(accepted);
            return;
        }

        try {
            handler.onAccepted(accepted);
        } catch (RuntimeException exception) {
            closeWithSuppressed(accepted, exception);
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.HANDLER_FAILED,
                            remoteAddress,
                            exception));
        }
    }

    private void releaseActiveConnection(long connectionId) {
        if (activeConnections.remove(connectionId) != null) {
            activeConnectionPermits.release();
        }
    }

    private void initiateClose(Throwable failure) {
        synchronized (lifecycleLock) {
            if (failure != null) {
                terminalFailure.compareAndSet(null, failure);
            }
            State current = state.get();
            if (current == State.CLOSING || current == State.CLOSED || current == State.FAILED) {
                return;
            }
            state.set(State.CLOSING);
            Thread closer = closeThreadFactory.newThread(this::runClose);
            closer.start();
        }
    }

    private void runClose() {
        List<Throwable> closeFailures = new ArrayList<>();
        closeAndCollect(serverSocket, closeFailures);
        for (Socket socket : List.copyOf(handshakeSockets)) {
            closeAndCollect(socket, closeFailures);
        }
        for (AcceptedTlsConnection connection : List.copyOf(activeConnections.values())) {
            closeAndCollect(connection, closeFailures);
        }

        handshakeExecutor.shutdown();
        awaitExecutor(closeFailures);
        joinAcceptThread(closeFailures);

        Throwable combined = terminalFailure.get();
        for (Throwable closeFailure : closeFailures) {
            combined = combine(combined, closeFailure);
        }
        if (combined != null) {
            terminalFailure.compareAndSet(null, combined);
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.SHUTDOWN_FAILED, null, combined));
            state.set(State.FAILED);
            closeFuture.completeExceptionally(combined);
        } else {
            state.set(State.CLOSED);
            closeFuture.complete(null);
        }
    }

    private void awaitExecutor(List<Throwable> failures) {
        long timeoutNanos = config.shutdownTimeout().toNanos();
        try {
            if (!handshakeExecutor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) {
                handshakeExecutor.shutdownNow();
                if (!handshakeExecutor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) {
                    failures.add(new IOException("TLS handshake executor did not terminate"));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            handshakeExecutor.shutdownNow();
            failures.add(
                    new IOException(
                            "interrupted while terminating the TLS handshake executor", exception));
        }
    }

    private void joinAcceptThread(List<Throwable> failures) {
        Thread thread = acceptThread;
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            long millis = config.shutdownTimeout().toMillis();
            int nanos = (int) config.shutdownTimeout().minusMillis(millis).toNanos();
            thread.join(millis, nanos);
            if (thread.isAlive()) {
                failures.add(new IOException("TLS accept thread did not terminate"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures.add(
                    new IOException("interrupted while joining the TLS accept thread", exception));
        }
    }

    private void closeRejectedSocket(Socket socket, SocketAddress remoteAddress) {
        try {
            socket.close();
        } catch (IOException exception) {
            publish(
                    Tls13ServerListenerEvent.failed(
                            Tls13ServerListenerEvent.Code.HANDSHAKE_FAILED,
                            remoteAddress,
                            exception));
        }
    }

    private void closeDuringShutdown(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception exception) {
            initiateClose(exception);
        }
    }

    private void publish(Tls13ServerListenerEvent event) {
        try {
            eventObserver.accept(event);
        } catch (RuntimeException ignored) {
            // An observer is diagnostic only and cannot control listener security or lifecycle.
        }
    }

    private static void closeAndCollect(AutoCloseable resource, List<Throwable> failures) {
        try {
            resource.close();
        } catch (Exception exception) {
            failures.add(exception);
        }
    }

    private static void closeWithSuppressed(AutoCloseable resource, Throwable primary) {
        try {
            resource.close();
        } catch (Exception closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    private static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary != primary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    private enum State {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED,
        FAILED
    }
}
