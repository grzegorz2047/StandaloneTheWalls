package pl.grzegorz2047.standalonethewalls.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustRecord;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStore;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;
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

class ServerLauncherTest {
    private static final long NETWORK_TIMEOUT_SECONDS = 10L;

    @TempDir Path temporaryDirectory;

    @Test
    void validatesConfigurationWithoutStartingTheRuntime() throws IOException {
        Path configuration = temporaryDirectory.resolve("server.properties");
        Files.writeString(configuration, "server.name=Validation Arena\nserver.tick-rate=20\n");

        assertEquals(
                ServerLauncher.EXIT_OK,
                ServerLauncher.run(
                        new String[] {"--config", configuration.toString(), "--validate-config"}));
    }

    @Test
    void validatesTlsCredentialsWithoutBindingAndRunsBoundedTlsLifecycle()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            ProcessConfiguration process = createProcessConfiguration(occupied.getLocalPort());
            assertEquals(
                    ServerLauncher.EXIT_OK,
                    ServerLauncher.run(
                            new String[] {
                                "--config",
                                process.server().toString(),
                                "--identity-config",
                                process.identity().toString(),
                                "--tls-config",
                                process.tls().toString(),
                                "--validate-config"
                            }));
        }

        int port = freePort();
        ProcessConfiguration process = createProcessConfiguration(port);
        assertEquals(
                ServerLauncher.EXIT_OK,
                ServerLauncher.run(
                        new String[] {
                            "--config",
                            process.server().toString(),
                            "--identity-config",
                            process.identity().toString(),
                            "--tls-config",
                            process.tls().toString(),
                            "--run-for-ticks",
                            "3"
                        }));
        try (ServerSocket rebound = new ServerSocket(port)) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    @Test
    void launcherTransfersIdentityAndAppliesTeamReadyCommandsInMinimalLobby()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    TlsTransportException,
                    TlsSessionBootstrapException,
                    PlayerSessionAdmissionException,
                    LobbyProtocolException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        int port = freePort();
        ProcessConfiguration process = createProcessConfiguration(port);
        CompletableFuture<Integer> launcherResult = new CompletableFuture<>();
        Thread launcherThread =
                Thread.ofVirtual()
                        .name("server-launcher-integration")
                        .start(
                                () -> {
                                    try {
                                        launcherResult.complete(
                                                ServerLauncher.run(
                                                        new String[] {
                                                            "--config",
                                                            process.server().toString(),
                                                            "--identity-config",
                                                            process.identity().toString(),
                                                            "--tls-config",
                                                            process.tls().toString(),
                                                            "--run-for-ticks",
                                                            "300"
                                                        }));
                                    } catch (Throwable failure) {
                                        launcherResult.completeExceptionally(failure);
                                    }
                                });

        ServerReference reference = new ServerReference("127.0.0.1:" + port);
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(
                        new ServerTrustService(new EmptyServerTrustStore()),
                        reference,
                        Optional.of(process.serverId()));
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("launcher_player");
        Tls13Connection connection = null;
        BootstrappedReliableSession bootstrapped = null;
        AuthenticatedReliableSession authenticated = null;
        try {
            connection = connectWithRetry(port, trustManager);
            bootstrapped =
                    TlsSessionBootstrap.connectClientSession(
                            connection, TlsSessionBootstrapConfig.DEFAULT);
            authenticated =
                    IdentityExchange.authenticateClient(
                                    bootstrapped,
                                    identity,
                                    handle,
                                    Clock.systemUTC(),
                                    IdentityExchangeConfig.DEFAULT)
                            .toCompletableFuture()
                            .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            bootstrapped = null;
            connection = null;

            ProtocolEnvelope admission = receive(authenticated);
            assertEquals(MessageType.SESSION_ADMISSION_RESULT, admission.messageType());
            assertEquals(
                    PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED,
                    PlayerSessionAdmissionCodec.decode(admission.payload()));

            ProtocolEnvelope joinedEnvelope = receive(authenticated);
            assertEquals(MessageType.LOBBY_JOINED, joinedEnvelope.messageType());
            LobbyJoined joined = LobbyProtocolCodec.decodeJoined(joinedEnvelope.payload());
            assertEquals(1L, joined.revision());
            assertEquals(identity.playerId(), joined.self().playerId());
            assertEquals(handle, joined.self().handle());

            ProtocolEnvelope snapshotEnvelope = receive(authenticated);
            assertEquals(MessageType.LOBBY_SNAPSHOT, snapshotEnvelope.messageType());
            LobbySnapshot snapshot = LobbyProtocolCodec.decodeSnapshot(snapshotEnvelope.payload());
            assertEquals(joined.revision(), snapshot.revision());
            assertEquals(java.util.List.of(joined.self()), snapshot.members());

            send(
                    authenticated,
                    MessageType.LOBBY_SELECT_TEAM,
                    LobbyProtocolCodec.encodeSelectTeam(
                            new LobbySelectTeamCommand(1L, LobbyTeam.GREEN)));
            LobbyCommandResult teamResult = receiveCommandResult(authenticated);
            assertEquals(new LobbyCommandResult(1L, 2L, LobbyCommandOutcome.APPLIED), teamResult);
            LobbySnapshot teamSnapshot = receiveSnapshot(authenticated);
            assertEquals(
                    java.util.List.of(
                            new LobbyMember(identity.playerId(), handle, LobbyTeam.GREEN, false)),
                    teamSnapshot.members());
            assertEquals(2L, teamSnapshot.revision());

            send(
                    authenticated,
                    MessageType.LOBBY_SET_READY,
                    LobbyProtocolCodec.encodeSetReady(new LobbySetReadyCommand(2L, true)));
            LobbyCommandResult readyResult = receiveCommandResult(authenticated);
            assertEquals(new LobbyCommandResult(2L, 3L, LobbyCommandOutcome.APPLIED), readyResult);
            LobbySnapshot readySnapshot = receiveSnapshot(authenticated);
            assertEquals(
                    java.util.List.of(
                            new LobbyMember(identity.playerId(), handle, LobbyTeam.GREEN, true)),
                    readySnapshot.members());
            assertEquals(3L, readySnapshot.revision());

            authenticated
                    .closeAsync()
                    .toCompletableFuture()
                    .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            authenticated = null;

            assertEquals(
                    ServerLauncher.EXIT_OK,
                    launcherResult.get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            launcherThread.join(TimeUnit.SECONDS.toMillis(NETWORK_TIMEOUT_SECONDS));
        } finally {
            closeForCleanup(authenticated, bootstrapped, connection);
        }
    }

    @Test
    void runsABoundedHeadlessSmokeAndRejectsBadArguments() {
        assertEquals(
                ServerLauncher.EXIT_OK, ServerLauncher.run(new String[] {"--run-for-ticks", "3"}));
        assertEquals(
                ServerLauncher.EXIT_USAGE_OR_CONFIGURATION,
                ServerLauncher.run(new String[] {"--run-for-ticks", "0"}));
        assertEquals(
                ServerLauncher.EXIT_USAGE_OR_CONFIGURATION,
                ServerLauncher.run(new String[] {"--unknown"}));
        assertEquals(
                ServerLauncher.EXIT_USAGE_OR_CONFIGURATION,
                ServerLauncher.run(
                        new String[] {
                            "--tls-config", "missing.properties", "--run-for-ticks", "1"
                        }));
    }

    private ProcessConfiguration createProcessConfiguration(int reliablePort)
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException {
        Path server = temporaryDirectory.resolve("server-" + reliablePort + ".properties");
        Files.writeString(
                server,
                "server.name=TLS Test Arena\n"
                        + "server.tick-rate=60\n"
                        + "server.reliable-port="
                        + reliablePort
                        + "\n"
                        + "server.realtime-port="
                        + differentPort(reliablePort)
                        + "\n"
                        + "server.maximum-players=4\n");

        KeyPair registryRoot = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path trustRoots = temporaryDirectory.resolve("registry-roots-" + reliablePort + ".hex");
        Files.writeString(
                trustRoots, HexFormat.of().formatHex(registryRoot.getPublic().getEncoded()) + "\n");
        Path identity = temporaryDirectory.resolve("identity-" + reliablePort + ".properties");
        Files.writeString(
                identity,
                "identity.sqlite-path=identity-"
                        + reliablePort
                        + ".sqlite\n"
                        + "identity.registry-bundle-path=registry-"
                        + reliablePort
                        + ".sfrb\n"
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path="
                        + trustRoots.getFileName()
                        + "\n"
                        + "identity.registry.refresh-source=LOCAL_BUNDLE\n");

        ServerTlsTestCertificateMaterial material =
                ServerTlsTestCertificateMaterial.create(reliablePort);
        Path privateKey = temporaryDirectory.resolve("server-key-" + reliablePort + ".pk8");
        Path certificate =
                temporaryDirectory.resolve("server-certificate-" + reliablePort + ".der");
        Files.write(privateKey, material.keyPair().getPrivate().getEncoded());
        Files.write(certificate, material.certificateDer());
        Path tls = temporaryDirectory.resolve("tls-" + reliablePort + ".properties");
        Files.writeString(
                tls,
                "transport.schema=1\n"
                        + "transport.reliable.bind-address=127.0.0.1\n"
                        + "transport.reliable.private-key-pkcs8-path="
                        + privateKey.getFileName()
                        + "\n"
                        + "transport.reliable.certificate-x509-path="
                        + certificate.getFileName()
                        + "\n"
                        + "transport.reliable.maximum-active-connections=4\n"
                        + "transport.identity.maximum-outstanding-challenges=4\n");
        return new ProcessConfiguration(
                server,
                identity,
                tls,
                ServerId.fromPublicKey(material.keyPair().getPublic().getEncoded()));
    }

    private static void send(
            AuthenticatedReliableSession session, MessageType messageType, byte[] payload)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.reliableChannel()
                .send(messageType, payload)
                .toCompletableFuture()
                .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static LobbyCommandResult receiveCommandResult(AuthenticatedReliableSession session)
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        ProtocolEnvelope envelope = receive(session);
        assertEquals(MessageType.LOBBY_COMMAND_RESULT, envelope.messageType());
        return LobbyProtocolCodec.decodeCommandResult(envelope.payload());
    }

    private static LobbySnapshot receiveSnapshot(AuthenticatedReliableSession session)
            throws InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    LobbyProtocolException {
        ProtocolEnvelope envelope = receive(session);
        assertEquals(MessageType.LOBBY_SNAPSHOT, envelope.messageType());
        return LobbyProtocolCodec.decodeSnapshot(envelope.payload());
    }

    private static ProtocolEnvelope receive(AuthenticatedReliableSession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        return session.reliableChannel()
                .receive()
                .toCompletableFuture()
                .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .orElseThrow();
    }

    private static Tls13Connection connectWithRetry(int port, PinnedServerTrustManager trustManager)
            throws IOException, TlsTransportException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(NETWORK_TIMEOUT_SECONDS);
        IOException lastConnectionFailure = null;
        while (System.nanoTime() < deadline) {
            Socket socket = new Socket();
            boolean connected = false;
            try {
                socket.connect(
                        new InetSocketAddress(
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port),
                        250);
                connected = true;
                socket.setSoTimeout(
                        Math.toIntExact(TimeUnit.SECONDS.toMillis(NETWORK_TIMEOUT_SECONDS)));
                return Tls13ClientConnector.connect(socket, trustManager, new SecureRandom());
            } catch (IOException failure) {
                try {
                    socket.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                if (connected) {
                    throw failure;
                }
                lastConnectionFailure = failure;
                Thread.sleep(10L);
            } catch (TlsTransportException | RuntimeException failure) {
                try {
                    socket.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }
        throw new IOException(
                "reliable TLS listener did not accept connections before the test deadline",
                lastConnectionFailure);
    }

    private static void closeForCleanup(
            AuthenticatedReliableSession authenticated,
            BootstrappedReliableSession bootstrapped,
            Tls13Connection connection) {
        try {
            if (authenticated != null) {
                authenticated
                        .closeAsync()
                        .toCompletableFuture()
                        .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } else if (bootstrapped != null) {
                bootstrapped
                        .closeAsync()
                        .toCompletableFuture()
                        .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } else if (connection != null) {
                connection.close();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException | ExecutionException | TimeoutException ignored) {
            // Cleanup must preserve the primary test failure.
        }
    }

    private static int differentPort(int reliablePort) {
        return reliablePort == 65_535 ? 65_534 : reliablePort + 1;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record ProcessConfiguration(Path server, Path identity, Path tls, ServerId serverId) {}

    private static final class EmptyServerTrustStore implements ServerTrustStore {
        @Override
        public Optional<ServerTrustRecord> find(ServerReference reference) {
            return Optional.empty();
        }

        @Override
        public boolean saveIfAbsent(ServerTrustRecord record) {
            return false;
        }

        @Override
        public boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement) {
            return false;
        }
    }
}
