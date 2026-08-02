package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
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
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ChallengeLedger;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallenge;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengePayload;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengeService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityPayloadCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityPayloadException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityProof;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultPayload;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;

class IdentityExchangeIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ServerReference REFERENCE = new ServerReference("localhost:25570");
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();
    private static final TlsSessionBootstrapConfig BOOTSTRAP_CONFIG =
            new TlsSessionBootstrapConfig(Duration.ofSeconds(2));
    private static final IdentityExchangeConfig EXCHANGE_CONFIG =
            new IdentityExchangeConfig(
                    Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(2));

    @Test
    void authenticatesOverRealTlsAndContinuesEnvelopeSequences()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    TlsSessionBootstrapException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup(61L, EXCHANGE_CONFIG, 2);
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        setup.listener().start();

        Tls13Connection clientConnection = null;
        BootstrappedReliableSession clientBootstrap = null;
        AuthenticatedReliableSession client = null;
        AuthenticatedReliableSession server = null;
        try {
            clientConnection = connectTls(setup.listener(), setup.trustManager());
            clientBootstrap =
                    TlsSessionBootstrap.connectClientSession(clientConnection, BOOTSTRAP_CONFIG);
            client =
                    await(
                            IdentityExchange.authenticateClient(
                                    clientBootstrap,
                                    identity,
                                    handle,
                                    Clock.systemUTC(),
                                    EXCHANGE_CONFIG));
            server = take(setup.authenticated(), "server identity result");

            assertThat(setup.failures()).isEmpty();
            assertThat(client.sessionId()).isEqualTo(server.sessionId());
            assertThat(client.security().serverId()).isEqualTo(server.security().serverId());
            assertThat(client.security().channelBinding())
                    .isEqualTo(server.security().channelBinding());
            assertThat(client.playerId()).isEqualTo(identity.playerId());
            assertThat(server.playerId()).isEqualTo(identity.playerId());
            assertThat(client.handle()).isEqualTo(handle);
            assertThat(server.handle()).isEqualTo(handle);
            assertThat(setup.challengeService().outstandingCount()).isZero();

            ReliableSendResult clientSend =
                    await(client.reliableChannel().send(MessageType.PING, new byte[] {1}));
            ProtocolEnvelope serverReceived =
                    await(server.reliableChannel().receive()).orElseThrow();
            assertThat(clientSend.sequence()).isEqualTo(1L);
            assertThat(serverReceived.sequence()).isEqualTo(1L);
            assertThat(serverReceived.payload()).containsExactly(1);

            ReliableSendResult serverSend =
                    await(server.reliableChannel().send(MessageType.PONG, new byte[] {2}));
            ProtocolEnvelope clientReceived =
                    await(client.reliableChannel().receive()).orElseThrow();
            assertThat(serverSend.sequence()).isEqualTo(2L);
            assertThat(clientReceived.sequence()).isEqualTo(2L);
            assertThat(clientReceived.payload()).containsExactly(2);

            await(client.closeAsync());
            await(server.closeAsync());
            waitUntil(
                    () -> setup.listener().activeConnectionCount() == 0,
                    "authenticated lease release");
        } finally {
            closeAuthenticatedForCleanup(client);
            closeAuthenticatedForCleanup(server);
            closeBootstrappedForCleanup(clientBootstrap);
            closeForCleanup(clientConnection);
            closeForCleanup(setup.listener());
        }
    }

    @Test
    void rejectsAProofReplayedIntoAnotherTlsSession()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    TlsSessionBootstrapException,
                    IdentityPayloadException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Setup setup = setup(62L, EXCHANGE_CONFIG, 1);
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        setup.listener().start();

        BootstrappedReliableSession firstClient = null;
        AuthenticatedReliableSession firstServer = null;
        BootstrappedReliableSession replayClient = null;
        try {
            firstClient = connectBootstrapped(setup);
            ProtocolEnvelope firstChallengeEnvelope = receive(firstClient);
            IdentityChallengePayload firstChallengePayload =
                    IdentityPayloadCodec.decodeChallenge(firstChallengeEnvelope.payload());
            IdentityChallenge firstChallenge = localChallenge(firstClient, firstChallengePayload);
            IdentityProof firstProof =
                    IdentityProof.create(identity, ProtocolVersion.CURRENT, firstChallenge, handle);
            byte[] replayedProofPayload = IdentityPayloadCodec.encodeProof(firstProof);

            assertThat(
                            await(
                                            firstClient
                                                    .reliableChannel()
                                                    .send(
                                                            MessageType.IDENTITY_PROOF,
                                                            replayedProofPayload))
                                    .sequence())
                    .isZero();
            IdentityResultPayload accepted =
                    IdentityPayloadCodec.decodeResult(receive(firstClient).payload());
            assertThat(accepted.status()).isEqualTo(IdentityResultStatus.ACCEPTED);
            firstServer = take(setup.authenticated(), "first accepted identity");
            await(firstClient.closeAsync());
            await(firstServer.closeAsync());
            waitUntil(
                    () -> setup.listener().activeConnectionCount() == 0,
                    "first identity lease release");
            firstClient = null;
            firstServer = null;

            replayClient = connectBootstrapped(setup);
            ProtocolEnvelope secondChallenge = receive(replayClient);
            assertThat(secondChallenge.messageType()).isEqualTo(MessageType.IDENTITY_CHALLENGE);
            await(
                    replayClient
                            .reliableChannel()
                            .send(MessageType.IDENTITY_PROOF, replayedProofPayload));
            IdentityResultPayload rejected =
                    IdentityPayloadCodec.decodeResult(receive(replayClient).payload());
            assertThat(rejected.status()).isEqualTo(IdentityResultStatus.INVALID_SIGNATURE);
            IdentityExchangeException serverFailure =
                    requireExchangeFailure(
                            take(setup.failures(), "replayed proof rejection"),
                            IdentityExchangeException.Code.REJECTED);
            assertThat(serverFailure.resultStatus())
                    .contains(IdentityResultStatus.INVALID_SIGNATURE);
            waitUntil(
                    () -> setup.listener().activeConnectionCount() == 0,
                    "replay rejection lease release");
            assertThat(setup.challengeService().outstandingCount()).isZero();
        } finally {
            closeAuthenticatedForCleanup(firstServer);
            closeBootstrappedForCleanup(firstClient);
            closeBootstrappedForCleanup(replayClient);
            closeForCleanup(setup.listener());
        }
    }

    @Test
    void identityTimeoutReleasesAdmissionForANextValidClient()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException,
                    TlsSessionBootstrapException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        IdentityExchangeConfig shortConfig =
                new IdentityExchangeConfig(
                        Duration.ofMillis(250), Duration.ofMillis(700), Duration.ofSeconds(1));
        Setup setup = setup(63L, shortConfig, 1);
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        setup.listener().start();

        BootstrappedReliableSession silentClient = null;
        BootstrappedReliableSession validBootstrap = null;
        AuthenticatedReliableSession validClient = null;
        AuthenticatedReliableSession validServer = null;
        try {
            silentClient = connectBootstrapped(setup);
            assertThat(receive(silentClient).messageType())
                    .isEqualTo(MessageType.IDENTITY_CHALLENGE);
            requireExchangeFailure(
                    take(setup.failures(), "identity proof timeout"),
                    IdentityExchangeException.Code.TIMEOUT);
            waitUntil(
                    () -> setup.listener().activeConnectionCount() == 0,
                    "identity timeout lease release");
            assertThat(setup.challengeService().outstandingCount()).isZero();
            closeBootstrappedForCleanup(silentClient);
            silentClient = null;

            validBootstrap = connectBootstrapped(setup);
            validClient =
                    await(
                            IdentityExchange.authenticateClient(
                                    validBootstrap,
                                    identity,
                                    new CanonicalHandle("player_one"),
                                    Clock.systemUTC(),
                                    shortConfig));
            validServer = take(setup.authenticated(), "valid identity after timeout");
            assertThat(validClient.playerId()).isEqualTo(validServer.playerId());
        } finally {
            closeAuthenticatedForCleanup(validClient);
            closeAuthenticatedForCleanup(validServer);
            closeBootstrappedForCleanup(silentClient);
            closeBootstrappedForCleanup(validBootstrap);
            closeForCleanup(setup.listener());
        }
    }

    private static Setup setup(
            long serial, IdentityExchangeConfig exchangeConfig, int maximumActiveConnections)
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
                REFERENCE, credentials.serverId(), Optional.empty(), "identity integration test");
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(trustService, REFERENCE, Optional.empty());
        IdentityChallengeService challengeService =
                new IdentityChallengeService(
                        new ChallengeLedger(
                                Clock.systemUTC(), new SecureRandom(), Duration.ofSeconds(3), 16));
        BlockingQueue<AuthenticatedReliableSession> authenticated = new LinkedBlockingQueue<>();
        BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        new Tls13ServerListenerConfig(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                16,
                                2,
                                maximumActiveConnections,
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(2)),
                        credentials,
                        connection -> {
                            try {
                                BootstrappedReliableSession session =
                                        TlsSessionBootstrap.acceptServerSession(
                                                connection, BOOTSTRAP_CONFIG, new SecureRandom());
                                IdentityExchange.authenticateServer(
                                                session, challengeService, exchangeConfig)
                                        .whenComplete(
                                                (accepted, failure) -> {
                                                    if (failure == null) {
                                                        authenticated.add(accepted);
                                                    } else {
                                                        failures.add(unwrap(failure));
                                                    }
                                                });
                            } catch (Exception exception) {
                                failures.add(exception);
                            }
                        },
                        event -> {});
        return new Setup(trustManager, challengeService, listener, authenticated, failures);
    }

    private static BootstrappedReliableSession connectBootstrapped(Setup setup)
            throws IOException, TlsTransportException, TlsSessionBootstrapException {
        Tls13Connection connection = connectTls(setup.listener(), setup.trustManager());
        try {
            return TlsSessionBootstrap.connectClientSession(connection, BOOTSTRAP_CONFIG);
        } catch (IOException | TlsSessionBootstrapException | RuntimeException exception) {
            try {
                connection.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
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

    private static IdentityChallenge localChallenge(
            BootstrappedReliableSession session, IdentityChallengePayload payload) {
        return new IdentityChallenge(
                session.security().serverId(),
                session.sessionId(),
                payload.nonce(),
                session.security().channelBinding(),
                payload.expiresAt());
    }

    private static ProtocolEnvelope receive(BootstrappedReliableSession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        return await(session.reliableChannel().receive()).orElseThrow();
    }

    private static IdentityExchangeException requireExchangeFailure(
            Throwable failure, IdentityExchangeException.Code expectedCode) {
        if (!(failure instanceof IdentityExchangeException exchangeFailure)) {
            throw new AssertionError("expected an identity exchange failure", failure);
        }
        assertThat(exchangeFailure.code()).isEqualTo(expectedCode);
        return exchangeFailure;
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

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void closeAuthenticatedForCleanup(AuthenticatedReliableSession session) {
        if (session == null) {
            return;
        }
        try {
            await(session.closeAsync());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while closing authenticated session", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("authenticated session cleanup failed", exception);
        }
    }

    private static void closeBootstrappedForCleanup(BootstrappedReliableSession session) {
        if (session == null) {
            return;
        }
        try {
            await(session.closeAsync());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while closing bootstrapped session", exception);
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
            PinnedServerTrustManager trustManager,
            IdentityChallengeService challengeService,
            Tls13ServerListener listener,
            BlockingQueue<AuthenticatedReliableSession> authenticated,
            BlockingQueue<Throwable> failures) {}
}
