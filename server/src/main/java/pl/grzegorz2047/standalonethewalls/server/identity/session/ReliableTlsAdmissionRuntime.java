package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ChallengeLedger;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengeService;
import pl.grzegorz2047.standalonethewalls.server.config.transport.ReliableTlsProcessConfiguration;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchangeConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListener;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListenerEvent;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapConfig;

/** Process-owned composition of the reliable TLS listener and mandatory identity admission. */
public final class ReliableTlsAdmissionRuntime implements AutoCloseable {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ReliableTlsAdmissionRuntime.class);

    private final TlsIdentityAdmissionGateway gateway;
    private final Tls13ServerListener listener;
    private final Object lifecycleLock = new Object();

    private State state = State.NEW;

    private ReliableTlsAdmissionRuntime(
            TlsIdentityAdmissionGateway gateway, Tls13ServerListener listener) {
        this.gateway = gateway;
        this.listener = listener;
    }

    public static ReliableTlsAdmissionRuntime open(
            ReliableTlsProcessConfiguration configuration,
            LocalIdentityRuntime identityRuntime,
            Clock clock)
            throws IOException {
        ReliableTlsProcessConfiguration transport =
                Objects.requireNonNull(configuration, "configuration");
        LocalIdentityRuntime identity =
                Objects.requireNonNull(identityRuntime, "identityRuntime");
        Clock timeSource = Objects.requireNonNull(clock, "clock");

        IdentityChallengeService challenges =
                new IdentityChallengeService(
                        new ChallengeLedger(
                                timeSource,
                                new SecureRandom(),
                                transport.challengeLifetime(),
                                transport.maximumOutstandingChallenges()));
        TlsIdentityAdmissionGateway gateway =
                new TlsIdentityAdmissionGateway(
                        identity,
                        challenges,
                        TlsSessionBootstrapConfig.DEFAULT,
                        IdentityExchangeConfig.DEFAULT,
                        transport.listenerConfig().maximumActiveConnections(),
                        transport.resultSendTimeout(),
                        transport.gatewayShutdownTimeout(),
                        ReliableTlsAdmissionRuntime::observeAdmission);
        try {
            Tls13ServerListener listener =
                    new Tls13ServerListener(
                            transport.listenerConfig(),
                            transport.credentials(),
                            gateway,
                            ReliableTlsAdmissionRuntime::observeListener);
            return new ReliableTlsAdmissionRuntime(gateway, listener);
        } catch (IOException | RuntimeException exception) {
            try {
                gateway.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    public void start() throws IOException {
        synchronized (lifecycleLock) {
            if (state != State.NEW) {
                throw new IllegalStateException("reliable TLS admission runtime can start only once");
            }
            try {
                listener.start();
                state = State.RUNNING;
            } catch (IOException | RuntimeException exception) {
                state = State.CLOSING;
                try {
                    closeResources();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                } finally {
                    state = State.CLOSED;
                }
                throw exception;
            }
        }
    }

    public InetSocketAddress localAddress() {
        return listener.localAddress();
    }

    public AuthorizedPlayerSessionQueue authorizedSessions() {
        return gateway.authorizedSessions();
    }

    public boolean isRunning() {
        synchronized (lifecycleLock) {
            return state == State.RUNNING && listener.isRunning() && gateway.isOpen();
        }
    }

    public Optional<Throwable> failure() {
        return listener.failure();
    }

    @Override
    public void close() throws IOException {
        synchronized (lifecycleLock) {
            if (state == State.CLOSED || state == State.CLOSING) {
                return;
            }
            state = State.CLOSING;
        }
        IOException failure;
        try {
            failure = closeResources();
        } finally {
            synchronized (lifecycleLock) {
                state = State.CLOSED;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private IOException closeResources() {
        List<Throwable> failures = new ArrayList<>();
        try {
            listener.close();
        } catch (IOException | RuntimeException exception) {
            failures.add(exception);
        }
        try {
            gateway.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        if (failures.isEmpty()) {
            return null;
        }
        IOException failure = new IOException("reliable TLS admission shutdown failed");
        failures.forEach(failure::addSuppressed);
        return failure;
    }

    private static void observeListener(Tls13ServerListenerEvent event) {
        LOGGER.warn("Reliable TLS listener event: {}.", event.code());
    }

    private static void observeAdmission(TlsIdentityAdmissionEvent event) {
        if (event.admissionStatus().isPresent()) {
            LOGGER.info("Player session admission result: {}.", event.admissionStatus().orElseThrow());
        } else {
            LOGGER.warn("Player session admission event: {}.", event.code());
        }
    }

    private enum State {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }
}
