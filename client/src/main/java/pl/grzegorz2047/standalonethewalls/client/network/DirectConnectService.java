package pl.grzegorz2047.standalonethewalls.client.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustDecision;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BootstrappedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchange;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchangeConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.PinnedServerTrustManager;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ClientConnector;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13Connection;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrap;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsTransportException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsTrustException;

/** Production client composition for reliable TLS connection and minimal lobby entry. */
public final class DirectConnectService implements AutoCloseable {
    private final ClientIdentityStorage storage;
    private final DirectConnectConfiguration configuration;
    private final Clock clock;
    private final SecureRandom random;
    private final AddressResolver addressResolver;
    private final ExecutorService workers;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Operation> active = new AtomicReference<>();
    private final AtomicReference<PendingConfirmation> pendingConfirmation =
            new AtomicReference<>();
    private final AtomicReference<ConnectedLobbySession> connected = new AtomicReference<>();

    public DirectConnectService(ClientIdentityStorage storage) {
        this(
                storage,
                DirectConnectConfiguration.DEFAULT,
                Clock.systemUTC(),
                new SecureRandom(),
                InetAddress::getAllByName);
    }

    public DirectConnectService(
            ClientIdentityStorage storage, DirectConnectConfiguration configuration) {
        this(
                storage,
                configuration,
                Clock.systemUTC(),
                new SecureRandom(),
                InetAddress::getAllByName);
    }

    DirectConnectService(
            ClientIdentityStorage storage,
            DirectConnectConfiguration configuration,
            Clock clock,
            SecureRandom random,
            AddressResolver addressResolver) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
        workers =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("sunderfront-direct-connect-", 0L).factory());
    }

    public DirectConnectAttempt connect(DirectConnectEndpoint endpoint, CanonicalHandle handle) {
        return connect(endpoint, handle, DirectConnectProgressListener.NONE);
    }

    public DirectConnectAttempt connect(
            DirectConnectEndpoint endpoint,
            CanonicalHandle handle,
            DirectConnectProgressListener progressListener) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(handle, "handle");
        pendingConfirmation.set(null);
        return start(
                endpoint,
                handle,
                Optional.empty(),
                false,
                DirectConnectProgressListener.require(progressListener));
    }

    public DirectConnectAttempt confirmFirstUse(FirstUseConfirmation confirmation) {
        return confirmFirstUse(confirmation, DirectConnectProgressListener.NONE);
    }

    public DirectConnectAttempt confirmFirstUse(
            FirstUseConfirmation confirmation,
            DirectConnectProgressListener progressListener) {
        Objects.requireNonNull(confirmation, "confirmation");
        DirectConnectProgressListener listener =
                DirectConnectProgressListener.require(progressListener);
        if (closed.get()) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.SERVICE_CLOSED);
        }
        PendingConfirmation pending = pendingConfirmation.getAndSet(null);
        if (pending == null || !pending.matches(confirmation)) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.CONFIRMATION_INVALID);
        }
        if (!clock.instant().isBefore(confirmation.expiresAt())) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.CONFIRMATION_EXPIRED);
        }
        return start(
                confirmation.endpoint(),
                pending.handle(),
                Optional.of(confirmation.serverId()),
                true,
                listener);
    }

    public void discardPendingConfirmation() {
        pendingConfirmation.set(null);
    }

    public boolean isConnecting() {
        return active.get() != null;
    }

    public Optional<ConnectedLobbySession> connectedSession() {
        ConnectedLobbySession session = connected.get();
        return session != null && session.isOpen() ? Optional.of(session) : Optional.empty();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pendingConfirmation.set(null);
        Operation operation = active.getAndSet(null);
        if (operation != null) {
            operation.cancel();
        }
        ConnectedLobbySession session = connected.getAndSet(null);
        if (session != null) {
            awaitClose(session.closeAsync());
        }
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(
                    configuration.closeTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException(
                        "direct connect workers did not terminate within the bounded timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while closing direct connect service", exception);
        }
    }

    private DirectConnectAttempt start(
            DirectConnectEndpoint endpoint,
            CanonicalHandle handle,
            Optional<ServerId> expectedServerId,
            boolean persistConfirmedServer,
            DirectConnectProgressListener progressListener) {
        if (closed.get()) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.SERVICE_CLOSED);
        }
        if (connected.get() != null) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.ALREADY_CONNECTED);
        }
        Operation operation =
                new Operation(
                        endpoint,
                        handle,
                        expectedServerId,
                        persistConfirmedServer,
                        progressListener);
        if (!active.compareAndSet(null, operation)) {
            return DirectConnectAttempt.completed(DirectConnectFailureCode.ALREADY_CONNECTING);
        }
        try {
            workers.execute(operation::run);
        } catch (RejectedExecutionException exception) {
            active.compareAndSet(operation, null);
            operation.completeFailure(DirectConnectFailureCode.SERVICE_CLOSED);
        }
        return operation.attempt();
    }

    private void awaitClose(CompletionStage<Void> stage) {
        try {
            Objects.requireNonNull(stage, "close stage")
                    .toCompletableFuture()
                    .get(configuration.closeTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            // Public close remains bounded and does not expose transport internals.
        }
    }

    private final class Operation {
        private final DirectConnectEndpoint endpoint;
        private final CanonicalHandle handle;
        private final Optional<ServerId> expectedServerId;
        private final boolean persistConfirmedServer;
        private final DirectConnectProgressListener progressListener;
        private final CompletableFuture<DirectConnectResult> result = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final Object resourceLock = new Object();

        private volatile Thread worker;
        private CloseAction resource = () -> {};
        private boolean ownershipTransferred;

        private Operation(
                DirectConnectEndpoint endpoint,
                CanonicalHandle handle,
                Optional<ServerId> expectedServerId,
                boolean persistConfirmedServer,
                DirectConnectProgressListener progressListener) {
            this.endpoint = endpoint;
            this.handle = handle;
            this.expectedServerId = expectedServerId;
            this.persistConfirmedServer = persistConfirmedServer;
            this.progressListener = DirectConnectProgressListener.require(progressListener);
        }

        private DirectConnectAttempt attempt() {
            return new DirectConnectAttempt(result, this::cancel);
        }

        private void run() {
            worker = Thread.currentThread();
            try {
                requireActive();
                PlayerIdentity identity =
                        PlayerIdentity.loadOrCreate(storage.playerIdentityStore(), random);
                ServerTrustService trustService =
                        new ServerTrustService(storage.serverTrustStore());
                PinnedServerTrustManager trustManager =
                        new PinnedServerTrustManager(
                                trustService, endpoint.serverReference(), expectedServerId);

                emit(DirectConnectStage.RESOLVING);
                Optional<InetAddress[]> addresses = resolveAddresses();
                if (addresses.isEmpty()) {
                    return;
                }
                emit(DirectConnectStage.CONNECTING);
                Socket socket = connectSocket(addresses.orElseThrow());
                if (socket == null) {
                    return;
                }
                installResource(() -> closeSocket(socket));
                requireActive();

                emit(DirectConnectStage.SECURING_TRANSPORT);
                Tls13Connection tls;
                try {
                    tls = Tls13ClientConnector.connect(socket, trustManager, random);
                } catch (IOException | TlsTransportException exception) {
                    completeTlsFailure(exception);
                    return;
                }
                installResource(() -> closeTls(tls));
                requireActive();

                if (persistConfirmedServer && !persistFirstUse(trustService, tls)) {
                    return;
                }

                BootstrappedReliableSession bootstrapped;
                try {
                    bootstrapped =
                            TlsSessionBootstrap.connectClientSession(
                                    tls, TlsSessionBootstrapConfig.DEFAULT);
                } catch (IOException | TlsSessionBootstrapException | RuntimeException exception) {
                    completeFailure(DirectConnectFailureCode.SESSION_BOOTSTRAP_FAILED);
                    return;
                }
                installResource(() -> awaitClose(bootstrapped.closeAsync()));
                requireActive();

                emit(DirectConnectStage.AUTHENTICATING);
                AuthenticatedReliableSession authenticated;
                try {
                    authenticated =
                            IdentityExchange.authenticateClient(
                                            bootstrapped,
                                            identity,
                                            handle,
                                            clock,
                                            IdentityExchangeConfig.DEFAULT)
                                    .toCompletableFuture()
                                    .get(
                                            configuration.protocolStepTimeout().toNanos(),
                                            TimeUnit.NANOSECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    completeFailure(
                            cancelled.get()
                                    ? DirectConnectFailureCode.CANCELLED
                                    : DirectConnectFailureCode.IDENTITY_EXCHANGE_FAILED);
                    return;
                } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                    completeFailure(DirectConnectFailureCode.IDENTITY_EXCHANGE_FAILED);
                    return;
                }
                installResource(() -> awaitClose(authenticated.closeAsync()));
                requireActive();

                emit(DirectConnectStage.WAITING_ADMISSION);
                PlayerSessionAdmissionStatus admission = receiveAdmission(authenticated);
                if (admission == null) {
                    return;
                }
                if (!admission.isAccepted()) {
                    complete(
                            new DirectConnectResult.Failed(
                                    DirectConnectFailure.admissionRejected(admission)));
                    return;
                }

                emit(DirectConnectStage.JOINING_LOBBY);
                LobbyJoined joined = receiveJoined(authenticated, identity);
                if (joined == null) {
                    return;
                }
                LobbySnapshot initialSnapshot = receiveInitialSnapshot(authenticated, joined);
                if (initialSnapshot == null) {
                    return;
                }
                try {
                    socket.setSoTimeout(0);
                } catch (IOException exception) {
                    completeFailure(DirectConnectFailureCode.INTERNAL_FAILURE);
                    return;
                }
                requireActive();

                ConnectedLobbySession lobbySession =
                        new ConnectedLobbySession(
                                authenticated,
                                initialSnapshot,
                                session -> connected.compareAndSet(session, null));
                if (!connected.compareAndSet(null, lobbySession)) {
                    awaitClose(lobbySession.closeAsync());
                    completeFailure(DirectConnectFailureCode.ALREADY_CONNECTED);
                    return;
                }
                if (!lobbySession.startReceiving()) {
                    connected.compareAndSet(lobbySession, null);
                    awaitClose(lobbySession.closeAsync());
                    completeFailure(DirectConnectFailureCode.INTERNAL_FAILURE);
                    return;
                }
                synchronized (resourceLock) {
                    ownershipTransferred = true;
                    resource = () -> {};
                }
                complete(new DirectConnectResult.Connected(lobbySession, admission));
            } catch (IdentityException exception) {
                completeFailure(DirectConnectFailureCode.IDENTITY_STORAGE_FAILED);
            } catch (CancelledOperation exception) {
                completeFailure(DirectConnectFailureCode.CANCELLED);
            } catch (RuntimeException exception) {
                completeFailure(DirectConnectFailureCode.INTERNAL_FAILURE);
            } finally {
                active.compareAndSet(this, null);
                if (!ownershipTransferred) {
                    closeCurrentResource();
                }
            }
        }

        private Optional<InetAddress[]> resolveAddresses() {
            Future<InetAddress[]> resolution;
            try {
                resolution = workers.submit(() -> addressResolver.resolve(endpoint.host()));
            } catch (RejectedExecutionException exception) {
                completeFailure(DirectConnectFailureCode.SERVICE_CLOSED);
                return Optional.empty();
            }
            try {
                InetAddress[] resolved =
                        resolution.get(
                                configuration.connectTimeout().toNanos(), TimeUnit.NANOSECONDS);
                if (resolved.length == 0) {
                    completeFailure(DirectConnectFailureCode.DNS_OR_CONNECT_FAILED);
                    return Optional.empty();
                }
                return Optional.of(resolved.clone());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                resolution.cancel(true);
                completeFailure(DirectConnectFailureCode.CANCELLED);
                return Optional.empty();
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                resolution.cancel(true);
                completeFailure(DirectConnectFailureCode.DNS_OR_CONNECT_FAILED);
                return Optional.empty();
            }
        }

        private Socket connectSocket(InetAddress[] addresses) {
            long deadline = System.nanoTime() + configuration.connectTimeout().toNanos();
            for (InetAddress address : addresses) {
                requireActive();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    break;
                }
                Socket socket = new Socket();
                try {
                    installResource(() -> closeSocket(socket));
                    socket.connect(
                            new InetSocketAddress(address, endpoint.port()),
                            Math.toIntExact(
                                    Math.max(
                                            1L,
                                            Math.min(
                                                    Integer.MAX_VALUE,
                                                    TimeUnit.NANOSECONDS.toMillis(remaining)))));
                    socket.setSoTimeout(
                            Math.toIntExact(configuration.socketReadTimeout().toMillis()));
                    return socket;
                } catch (IOException | RuntimeException exception) {
                    closeSocket(socket);
                }
            }
            completeFailure(DirectConnectFailureCode.DNS_OR_CONNECT_FAILED);
            return null;
        }

        private boolean persistFirstUse(ServerTrustService trustService, Tls13Connection tls) {
            try {
                trustService.confirmFirstUse(
                        endpoint.serverReference(),
                        tls.security().serverId(),
                        Optional.empty(),
                        "explicit Direct Connect first-use confirmation");
                return true;
            } catch (IllegalStateException conflict) {
                try {
                    ServerTrustDecision current =
                            trustService.inspect(
                                    endpoint.serverReference(),
                                    tls.security().serverId(),
                                    Optional.empty());
                    if (current.isTrusted()) {
                        return true;
                    }
                } catch (ServerTrustStoreException ignored) {
                    // Mapped below to one stable public storage failure.
                }
                completeFailure(DirectConnectFailureCode.TRUST_STORAGE_FAILED);
                return false;
            } catch (ServerTrustStoreException exception) {
                completeFailure(DirectConnectFailureCode.TRUST_STORAGE_FAILED);
                return false;
            }
        }

        private PlayerSessionAdmissionStatus receiveAdmission(
                AuthenticatedReliableSession authenticated) {
            ProtocolEnvelope envelope =
                    receive(
                            authenticated,
                            DirectConnectFailureCode.ADMISSION_TIMEOUT,
                            DirectConnectFailureCode.ADMISSION_MALFORMED);
            if (envelope == null) {
                return null;
            }
            if (envelope.messageType() != MessageType.SESSION_ADMISSION_RESULT) {
                completeFailure(DirectConnectFailureCode.UNEXPECTED_MESSAGE);
                return null;
            }
            try {
                return PlayerSessionAdmissionCodec.decode(envelope.payload());
            } catch (PlayerSessionAdmissionException exception) {
                completeFailure(DirectConnectFailureCode.ADMISSION_MALFORMED);
                return null;
            }
        }

        private LobbyJoined receiveJoined(
                AuthenticatedReliableSession authenticated, PlayerIdentity identity) {
            ProtocolEnvelope envelope =
                    receive(
                            authenticated,
                            DirectConnectFailureCode.LOBBY_JOIN_TIMEOUT,
                            DirectConnectFailureCode.LOBBY_JOIN_MALFORMED);
            if (envelope == null) {
                return null;
            }
            if (envelope.messageType() != MessageType.LOBBY_JOINED) {
                completeFailure(DirectConnectFailureCode.UNEXPECTED_MESSAGE);
                return null;
            }
            try {
                LobbyJoined joined = LobbyProtocolCodec.decodeJoined(envelope.payload());
                if (!joined.self().playerId().equals(identity.playerId())
                        || !joined.self().handle().equals(handle)) {
                    completeFailure(DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH);
                    return null;
                }
                return joined;
            } catch (LobbyProtocolException exception) {
                completeFailure(DirectConnectFailureCode.LOBBY_JOIN_MALFORMED);
                return null;
            }
        }

        private LobbySnapshot receiveInitialSnapshot(
                AuthenticatedReliableSession authenticated, LobbyJoined joined) {
            ProtocolEnvelope envelope =
                    receive(
                            authenticated,
                            DirectConnectFailureCode.LOBBY_SNAPSHOT_TIMEOUT,
                            DirectConnectFailureCode.LOBBY_SNAPSHOT_MALFORMED);
            if (envelope == null) {
                return null;
            }
            if (envelope.messageType() != MessageType.LOBBY_SNAPSHOT) {
                completeFailure(DirectConnectFailureCode.UNEXPECTED_MESSAGE);
                return null;
            }
            try {
                LobbySnapshot snapshot = LobbyProtocolCodec.decodeSnapshot(envelope.payload());
                if (snapshot.revision() < joined.revision()) {
                    completeFailure(DirectConnectFailureCode.LOBBY_SNAPSHOT_STALE);
                    return null;
                }
                if (!ConnectedLobbySession.containsExactSelf(
                        snapshot, joined.self().playerId(), handle)) {
                    completeFailure(DirectConnectFailureCode.LOBBY_IDENTITY_MISMATCH);
                    return null;
                }
                return snapshot;
            } catch (LobbyProtocolException exception) {
                completeFailure(DirectConnectFailureCode.LOBBY_SNAPSHOT_MALFORMED);
                return null;
            }
        }

        private ProtocolEnvelope receive(
                AuthenticatedReliableSession authenticated,
                DirectConnectFailureCode timeoutCode,
                DirectConnectFailureCode malformedCode) {
            try {
                Optional<ProtocolEnvelope> received =
                        Objects.requireNonNull(
                                        authenticated.reliableChannel().receive(), "receive stage")
                                .toCompletableFuture()
                                .get(
                                        configuration.protocolStepTimeout().toNanos(),
                                        TimeUnit.NANOSECONDS);
                if (received.isEmpty()) {
                    completeFailure(DirectConnectFailureCode.CONNECTION_CLOSED);
                    return null;
                }
                return received.orElseThrow();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                completeFailure(cancelled.get() ? DirectConnectFailureCode.CANCELLED : timeoutCode);
                return null;
            } catch (TimeoutException exception) {
                completeFailure(timeoutCode);
                return null;
            } catch (ExecutionException | RuntimeException exception) {
                completeFailure(malformedCode);
                return null;
            }
        }

        private void completeTlsFailure(Throwable exception) {
            TlsTrustException trustFailure = findCause(exception, TlsTrustException.class);
            if (trustFailure != null) {
                switch (trustFailure.status()) {
                    case FIRST_USE_REQUIRES_CONFIRMATION -> issueConfirmation(trustFailure);
                    case CHANGED_IDENTITY ->
                            completeFailure(DirectConnectFailureCode.CHANGED_SERVER_IDENTITY);
                    case EXPECTED_PIN_MISMATCH ->
                            completeFailure(
                                    DirectConnectFailureCode.EXPECTED_SERVER_IDENTITY_MISMATCH);
                    case TRUSTED -> completeFailure(DirectConnectFailureCode.TLS_HANDSHAKE_FAILED);
                }
                return;
            }
            if (findCause(exception, ServerTrustStoreException.class) != null) {
                completeFailure(DirectConnectFailureCode.TRUST_STORAGE_FAILED);
            } else {
                completeFailure(DirectConnectFailureCode.TLS_HANDSHAKE_FAILED);
            }
        }

        private void issueConfirmation(TlsTrustException trustFailure) {
            requireActive();
            byte[] tokenBytes = new byte[32];
            random.nextBytes(tokenBytes);
            FirstUseConfirmation confirmation =
                    new FirstUseConfirmation(
                            endpoint,
                            trustFailure.presentedServerId(),
                            trustFailure.fingerprint(),
                            clock.instant().plus(configuration.confirmationLifetime()),
                            new DirectConnectConfirmationToken(tokenBytes));
            java.util.Arrays.fill(tokenBytes, (byte) 0);
            pendingConfirmation.set(new PendingConfirmation(confirmation, handle));
            complete(new DirectConnectResult.ConfirmationRequired(confirmation));
        }

        private void emit(DirectConnectStage stage) {
            if (cancelled.get() || closed.get()) {
                return;
            }
            try {
                progressListener.onStage(Objects.requireNonNull(stage, "stage"));
            } catch (RuntimeException ignored) {
                // Progress observers cannot control transport ownership or lifecycle.
            }
        }

        private boolean cancel() {
            if (result.isDone() || !cancelled.compareAndSet(false, true)) {
                return false;
            }
            pendingConfirmation.set(null);
            Thread currentWorker = worker;
            if (currentWorker != null) {
                currentWorker.interrupt();
            }
            ConnectedLobbySession stagedSession = connected.get();
            if (stagedSession != null) {
                awaitClose(stagedSession.closeAsync());
            }
            closeCurrentResource();
            completeFailure(DirectConnectFailureCode.CANCELLED);
            active.compareAndSet(this, null);
            return true;
        }

        private void installResource(CloseAction next) {
            Objects.requireNonNull(next, "next");
            synchronized (resourceLock) {
                if (cancelled.get() || closed.get()) {
                    next.close();
                    throw new CancelledOperation();
                }
                resource = next;
            }
        }

        private void closeCurrentResource() {
            CloseAction current;
            synchronized (resourceLock) {
                current = resource;
                resource = () -> {};
            }
            try {
                current.close();
            } catch (RuntimeException ignored) {
                // Cleanup cannot replace the stable public operation result.
            }
        }

        private void requireActive() {
            if (cancelled.get() || closed.get()) {
                throw new CancelledOperation();
            }
        }

        private void completeFailure(DirectConnectFailureCode code) {
            complete(new DirectConnectResult.Failed(DirectConnectFailure.of(code)));
        }

        private void complete(DirectConnectResult terminalResult) {
            closeCurrentResource();
            active.compareAndSet(this, null);
            result.complete(terminalResult);
        }
    }

    private record PendingConfirmation(FirstUseConfirmation confirmation, CanonicalHandle handle) {
        private PendingConfirmation {
            Objects.requireNonNull(confirmation, "confirmation");
            Objects.requireNonNull(handle, "handle");
        }

        private boolean matches(FirstUseConfirmation supplied) {
            return confirmation.endpoint().equals(supplied.endpoint())
                    && confirmation.serverId().equals(supplied.serverId())
                    && confirmation.fingerprint().equals(supplied.fingerprint())
                    && confirmation.expiresAt().equals(supplied.expiresAt())
                    && confirmation.token().securelyEquals(supplied.token());
        }
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws IOException;
    }

    @FunctionalInterface
    private interface CloseAction {
        void close();
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Cleanup must preserve the stable public result.
        }
    }

    private static void closeTls(Tls13Connection tls) {
        try {
            tls.close();
        } catch (IOException ignored) {
            // Cleanup must preserve the stable public result.
        }
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class CancelledOperation extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
