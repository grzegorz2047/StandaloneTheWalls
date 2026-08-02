package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengeService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AcceptedTlsConnection;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BootstrappedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchange;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchangeConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13AcceptedConnectionHandler;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrap;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapException;

/**
 * Owns TLS bootstrap, identity proof, policy admission, the admission-result message, and the
 * bounded pre-lobby handoff queue.
 *
 * <p>Every accepted connection is processed on an owned virtual thread. No network, SQLite, or
 * registry operation executes on the listener accept thread or the fixed-tick simulation thread.
 */
public final class TlsIdentityAdmissionGateway
        implements Tls13AcceptedConnectionHandler, AutoCloseable {
    private static final AtomicLong GATEWAY_IDS = new AtomicLong();
    private static final Duration MAXIMUM_OPERATION_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration MAXIMUM_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final AuthenticatedPlayerAdmissionService admissionService;
    private final IdentityChallengeService challengeService;
    private final TlsSessionBootstrapConfig bootstrapConfig;
    private final IdentityExchangeConfig exchangeConfig;
    private final Duration resultSendTimeout;
    private final Duration shutdownTimeout;
    private final Duration identityWaitTimeout;
    private final AuthorizedPlayerSessionQueue authorizedSessions;
    private final Consumer<TlsIdentityAdmissionEvent> eventObserver;
    private final Supplier<SecureRandom> secureRandomSupplier;
    private final ExecutorService executor;
    private final ConcurrentMap<Long, AcceptedTlsConnection> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private final Object lifecycleLock = new Object();

    public TlsIdentityAdmissionGateway(
            LocalIdentityRuntime identityRuntime,
            IdentityChallengeService challengeService,
            TlsSessionBootstrapConfig bootstrapConfig,
            IdentityExchangeConfig exchangeConfig,
            int queueCapacity,
            Duration resultSendTimeout,
            Duration shutdownTimeout,
            Consumer<TlsIdentityAdmissionEvent> eventObserver) {
        this(
                new AuthenticatedPlayerAdmissionService(identityRuntime),
                challengeService,
                bootstrapConfig,
                exchangeConfig,
                queueCapacity,
                resultSendTimeout,
                shutdownTimeout,
                eventObserver,
                SecureRandom::new);
    }

    TlsIdentityAdmissionGateway(
            AuthenticatedPlayerAdmissionService admissionService,
            IdentityChallengeService challengeService,
            TlsSessionBootstrapConfig bootstrapConfig,
            IdentityExchangeConfig exchangeConfig,
            int queueCapacity,
            Duration resultSendTimeout,
            Duration shutdownTimeout,
            Consumer<TlsIdentityAdmissionEvent> eventObserver,
            Supplier<SecureRandom> secureRandomSupplier) {
        this.admissionService = Objects.requireNonNull(admissionService, "admissionService");
        this.challengeService = Objects.requireNonNull(challengeService, "challengeService");
        this.bootstrapConfig = Objects.requireNonNull(bootstrapConfig, "bootstrapConfig");
        this.exchangeConfig = Objects.requireNonNull(exchangeConfig, "exchangeConfig");
        this.resultSendTimeout =
                requireDuration(
                        resultSendTimeout, "resultSendTimeout", MAXIMUM_OPERATION_TIMEOUT);
        this.shutdownTimeout =
                requireDuration(shutdownTimeout, "shutdownTimeout", MAXIMUM_SHUTDOWN_TIMEOUT);
        this.identityWaitTimeout =
                exchangeConfig
                        .overallTimeout()
                        .plus(exchangeConfig.closeTimeout())
                        .plusSeconds(1L);
        this.authorizedSessions =
                new AuthorizedPlayerSessionQueue(queueCapacity, exchangeConfig.closeTimeout());
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
        this.secureRandomSupplier =
                Objects.requireNonNull(secureRandomSupplier, "secureRandomSupplier");
        long gatewayId = GATEWAY_IDS.incrementAndGet();
        this.executor =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual()
                                .name("sunderfront-identity-admission-" + gatewayId + '-', 0L)
                                .factory());
    }

    public AuthorizedPlayerSessionQueue authorizedSessions() {
        return authorizedSessions;
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    @Override
    public void onAccepted(AcceptedTlsConnection connection) {
        AcceptedTlsConnection accepted = Objects.requireNonNull(connection, "connection");
        synchronized (lifecycleLock) {
            if (state.get() != State.OPEN) {
                closeConnection(accepted);
                publish(
                        TlsIdentityAdmissionEvent.failure(
                                TlsIdentityAdmissionEvent.Code.GATEWAY_CLOSED));
                return;
            }
            if (inFlight.putIfAbsent(accepted.connectionId(), accepted) != null) {
                closeConnection(accepted);
                publish(
                        TlsIdentityAdmissionEvent.failure(
                                TlsIdentityAdmissionEvent.Code.INTERNAL_FAILURE));
                return;
            }
            try {
                executor.execute(() -> process(accepted));
            } catch (RejectedExecutionException exception) {
                inFlight.remove(accepted.connectionId(), accepted);
                closeConnection(accepted);
                publish(
                        TlsIdentityAdmissionEvent.failure(
                                TlsIdentityAdmissionEvent.Code.GATEWAY_CLOSED));
            }
        }
    }

    @Override
    public void close() {
        List<AcceptedTlsConnection> activeConnections;
        synchronized (lifecycleLock) {
            State current = state.get();
            if (current == State.CLOSED || current == State.CLOSING) {
                return;
            }
            state.set(State.CLOSING);
            activeConnections = List.copyOf(inFlight.values());
            executor.shutdownNow();
        }

        List<Throwable> failures = new ArrayList<>();
        for (AcceptedTlsConnection connection : activeConnections) {
            try {
                connection.close();
            } catch (IOException exception) {
                failures.add(exception);
            }
        }
        try {
            authorizedSessions.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        try {
            if (!executor.awaitTermination(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                failures.add(
                        new IllegalStateException(
                                "identity admission executor did not terminate"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures.add(
                    new IllegalStateException(
                            "interrupted while closing identity admission gateway", exception));
        }
        state.set(State.CLOSED);
        if (!failures.isEmpty()) {
            IllegalStateException failure =
                    new IllegalStateException("identity admission gateway close failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private void process(AcceptedTlsConnection connection) {
        AuthenticatedReliableSession authenticated = null;
        boolean transferred = false;
        try {
            BootstrappedReliableSession bootstrapped =
                    TlsSessionBootstrap.acceptServerSession(
                            connection,
                            bootstrapConfig,
                            Objects.requireNonNull(
                                    secureRandomSupplier.get(),
                                    "secureRandomSupplier returned null"));
            if (state.get() != State.OPEN) {
                publish(
                        TlsIdentityAdmissionEvent.failure(
                                TlsIdentityAdmissionEvent.Code.GATEWAY_CLOSED));
                closeWithin(bootstrapped.closeAsync());
                return;
            }
            authenticated =
                    await(
                            IdentityExchange.authenticateServer(
                                    bootstrapped, challengeService, exchangeConfig),
                            identityWaitTimeout);
            if (state.get() != State.OPEN) {
                publish(
                        TlsIdentityAdmissionEvent.failure(
                                TlsIdentityAdmissionEvent.Code.GATEWAY_CLOSED));
                return;
            }
            BctlsAuthenticatedPlayerSession session =
                    new BctlsAuthenticatedPlayerSession(authenticated);
            AuthenticatedPlayerAdmissionResult admission = admissionService.evaluate(session);
            if (admission instanceof AuthenticatedPlayerAdmissionResult.Rejected rejected) {
                sendAdmissionResult(session, rejected.status());
                publish(TlsIdentityAdmissionEvent.admission(rejected.status()));
                return;
            }

            AuthenticatedPlayerAdmissionResult.Accepted accepted =
                    (AuthenticatedPlayerAdmissionResult.Accepted) admission;
            Optional<AuthorizedPlayerSessionQueue.Reservation> reservationAttempt =
                    authorizedSessions.tryReserve();
            if (reservationAttempt.isEmpty()) {
                PlayerSessionAdmissionStatus status =
                        authorizedSessions.isClosed() || state.get() != State.OPEN
                                ? PlayerSessionAdmissionStatus.SERVER_SHUTTING_DOWN
                                : PlayerSessionAdmissionStatus.SERVER_CAPACITY_EXCEEDED;
                sendAdmissionResult(session, status);
                publish(TlsIdentityAdmissionEvent.admission(status));
                return;
            }

            try (AuthorizedPlayerSessionQueue.Reservation reservation =
                    reservationAttempt.orElseThrow()) {
                if (state.get() != State.OPEN) {
                    sendAdmissionResult(
                            session, PlayerSessionAdmissionStatus.SERVER_SHUTTING_DOWN);
                    publish(
                            TlsIdentityAdmissionEvent.admission(
                                    PlayerSessionAdmissionStatus.SERVER_SHUTTING_DOWN));
                    return;
                }
                sendAdmissionResult(session, accepted.status());
                if (!reservation.commit(accepted.session())) {
                    publish(
                            TlsIdentityAdmissionEvent.admission(
                                    PlayerSessionAdmissionStatus.SERVER_SHUTTING_DOWN));
                    return;
                }
                transferred = true;
                publish(TlsIdentityAdmissionEvent.admission(accepted.status()));
            }
        } catch (IOException | TlsSessionBootstrapException exception) {
            publish(
                    TlsIdentityAdmissionEvent.failure(
                            TlsIdentityAdmissionEvent.Code.BOOTSTRAP_FAILED));
        } catch (TimeoutException | ExecutionException exception) {
            publish(
                    TlsIdentityAdmissionEvent.failure(
                            TlsIdentityAdmissionEvent.Code.IDENTITY_EXCHANGE_FAILED));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            publish(
                    TlsIdentityAdmissionEvent.failure(
                            TlsIdentityAdmissionEvent.Code.GATEWAY_CLOSED));
        } catch (AdmissionResultSendException exception) {
            publish(
                    TlsIdentityAdmissionEvent.failure(
                            TlsIdentityAdmissionEvent.Code.ADMISSION_RESULT_SEND_FAILED));
        } catch (RuntimeException exception) {
            publish(
                    TlsIdentityAdmissionEvent.failure(
                            TlsIdentityAdmissionEvent.Code.INTERNAL_FAILURE));
        } finally {
            inFlight.remove(connection.connectionId(), connection);
            if (!transferred) {
                if (authenticated == null) {
                    closeConnection(connection);
                } else {
                    closeWithin(authenticated.closeAsync());
                }
            }
        }
    }

    private void sendAdmissionResult(
            AuthenticatedPlayerSession session, PlayerSessionAdmissionStatus status)
            throws AdmissionResultSendException, InterruptedException {
        try {
            await(
                    session.reliableChannel()
                            .send(
                                    MessageType.SESSION_ADMISSION_RESULT,
                                    PlayerSessionAdmissionCodec.encode(status)),
                    resultSendTimeout);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AdmissionResultSendException(exception);
        }
    }

    private void closeWithin(CompletionStage<Void> closeStage) {
        try {
            await(closeStage, exchangeConfig.closeTimeout());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            // Closing is best-effort here; the listener lease is independently idempotent.
        }
    }

    private static <T> T await(CompletionStage<T> stage, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return Objects.requireNonNull(stage, "stage")
                .toCompletableFuture()
                .get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void closeConnection(AcceptedTlsConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // Listener admission release remains idempotent and there is no safe public detail.
        }
    }

    private void publish(TlsIdentityAdmissionEvent event) {
        try {
            eventObserver.accept(event);
        } catch (RuntimeException ignored) {
            // Diagnostic observers cannot control admission or resource ownership.
        }
    }

    private static Duration requireDuration(Duration value, String field, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero()
                || duration.isNegative()
                || duration.compareTo(maximum) > 0
                || duration.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        return duration;
    }

    private enum State {
        OPEN,
        CLOSING,
        CLOSED
    }

    private static final class AdmissionResultSendException extends Exception {
        private static final long serialVersionUID = 1L;

        private AdmissionResultSendException(Throwable cause) {
            super("player session admission result could not be sent", unwrap(cause));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
