package pl.grzegorz2047.standalonethewalls.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.client.network.ConnectedLobbySession;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectAttempt;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectEndpoint;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectResult;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.network.FirstUseConfirmation;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;

class DirectConnectServiceLoopbackTest {
    private static final long NETWORK_TIMEOUT_SECONDS = 15L;

    @TempDir Path temporaryDirectory;

    @Test
    void entersLobbyAfterExplicitFirstUseAndReturnsAfterClientRestart() throws Exception {
        int reliablePort = freePort();
        ProcessConfiguration process = createProcessConfiguration(reliablePort);
        CompletableFuture<Integer> launcherResult = startServer(process);
        awaitListener(reliablePort);

        Path clientData = temporaryDirectory.resolve("client-data");
        DirectConnectEndpoint endpoint =
                DirectConnectEndpoint.parse("127.0.0.1:" + reliablePort);
        CanonicalHandle handle = new CanonicalHandle("direct_player");
        PlayerId persistedPlayerId;

        try (DirectConnectService firstClient =
                new DirectConnectService(new ClientIdentityStorage(clientData))) {
            DirectConnectResult unknown = await(firstClient.connect(endpoint, handle));
            DirectConnectResult.ConfirmationRequired confirmationRequired =
                    assertInstanceOf(DirectConnectResult.ConfirmationRequired.class, unknown);
            FirstUseConfirmation confirmation = confirmationRequired.confirmation();
            assertEquals(endpoint, confirmation.endpoint());

            DirectConnectResult.Connected firstConnected =
                    assertInstanceOf(
                            DirectConnectResult.Connected.class,
                            await(firstClient.confirmFirstUse(confirmation)));
            assertEquals(
                    PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED,
                    firstConnected.admissionStatus());
            persistedPlayerId = firstConnected.session().playerId();
            assertLobbyContainsSelf(firstConnected.session(), persistedPlayerId, handle);
            close(firstConnected.session());
        }

        try (DirectConnectService restartedClient =
                new DirectConnectService(new ClientIdentityStorage(clientData))) {
            DirectConnectResult.Connected returning =
                    assertInstanceOf(
                            DirectConnectResult.Connected.class,
                            await(restartedClient.connect(endpoint, handle)));
            assertEquals(
                    PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED,
                    returning.admissionStatus());
            assertEquals(persistedPlayerId, returning.session().playerId());
            assertLobbyContainsSelf(returning.session(), persistedPlayerId, handle);
            close(returning.session());
        }

        assertEquals(
                ServerLauncher.EXIT_OK,
                launcherResult.get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private CompletableFuture<Integer> startServer(ProcessConfiguration process) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("direct-connect-server-launcher")
                .start(
                        () -> {
                            try {
                                result.complete(
                                        ServerLauncher.run(
                                                new String[] {
                                                    "--config",
                                                    process.server().toString(),
                                                    "--identity-config",
                                                    process.identity().toString(),
                                                    "--tls-config",
                                                    process.tls().toString(),
                                                    "--run-for-ticks",
                                                    "480"
                                                }));
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        });
        return result;
    }

    private ProcessConfiguration createProcessConfiguration(int reliablePort)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        Path server = temporaryDirectory.resolve("direct-server.properties");
        Files.writeString(
                server,
                "server.name=Direct Connect Arena\n"
                        + "server.tick-rate=60\n"
                        + "server.reliable-port="
                        + reliablePort
                        + "\n"
                        + "server.realtime-port="
                        + differentPort(reliablePort)
                        + "\n"
                        + "server.maximum-players=4\n");

        KeyPair registryRoot = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path trustRoots = temporaryDirectory.resolve("direct-registry-roots.hex");
        Files.writeString(
                trustRoots, HexFormat.of().formatHex(registryRoot.getPublic().getEncoded()) + "\n");
        Path identity = temporaryDirectory.resolve("direct-identity.properties");
        Files.writeString(
                identity,
                "identity.sqlite-path=direct-identity.sqlite\n"
                        + "identity.registry-bundle-path=direct-registry.sfrb\n"
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path="
                        + trustRoots.getFileName()
                        + "\n"
                        + "identity.registry.refresh-source=LOCAL_BUNDLE\n");

        ServerTlsTestCertificateMaterial material =
                ServerTlsTestCertificateMaterial.create(reliablePort);
        Path privateKey = temporaryDirectory.resolve("direct-server-key.pk8");
        Path certificate = temporaryDirectory.resolve("direct-server-certificate.der");
        Files.write(privateKey, material.keyPair().getPrivate().getEncoded());
        Files.write(certificate, material.certificateDer());
        Path tls = temporaryDirectory.resolve("direct-tls.properties");
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
        return new ProcessConfiguration(server, identity, tls);
    }

    private static DirectConnectResult await(DirectConnectAttempt attempt) throws Exception {
        return attempt.result()
                .toCompletableFuture()
                .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void close(ConnectedLobbySession session) throws Exception {
        session.closeAsync()
                .toCompletableFuture()
                .get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void assertLobbyContainsSelf(
            ConnectedLobbySession session, PlayerId playerId, CanonicalHandle handle) {
        assertTrue(
                session.currentSnapshot().members().stream()
                        .anyMatch(
                                member ->
                                        member.playerId().equals(playerId)
                                                && member.handle().equals(handle)));
    }

    private static void awaitListener(int port) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(NETWORK_TIMEOUT_SECONDS);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                Thread.sleep(10L);
            }
        }
        throw new IOException("server listener did not start before the deadline", lastFailure);
    }

    private static int differentPort(int reliablePort) {
        return reliablePort == 65_535 ? 65_534 : reliablePort + 1;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record ProcessConfiguration(Path server, Path identity, Path tls) {}
}
