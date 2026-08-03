package pl.grzegorz2047.standalonethewalls.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.identity.ClientIdentityStorage;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectConfiguration;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectService;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiController;
import pl.grzegorz2047.standalonethewalls.client.ui.directconnect.DirectConnectUiPhase;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;

class DirectConnectUiLoopbackTest {
    private static final long NETWORK_TIMEOUT_SECONDS = 15L;
    private static final DirectConnectConfiguration CONFIGURATION =
            new DirectConnectConfiguration(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(5),
                    Duration.ofMinutes(2));

    @TempDir Path temporaryDirectory;

    @Test
    void marshalsRealFirstUseAndLobbyResultsOntoTheUiOwner()
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        int reliablePort = freePort();
        ProcessConfiguration process = createProcessConfiguration(reliablePort);
        CompletableFuture<Integer> launcherResult = startServer(process);
        awaitListener(reliablePort);

        Thread uiOwner = Thread.currentThread();
        BlockingQueue<Runnable> uiTasks = new LinkedBlockingQueue<>();
        Queue<Thread> callbackSources = new ConcurrentLinkedQueue<>();
        AtomicBoolean observerLeftUiOwner = new AtomicBoolean();
        DirectConnectService service =
                new DirectConnectService(
                        new ClientIdentityStorage(temporaryDirectory.resolve("ui-client-data")),
                        CONFIGURATION);
        DirectConnectUiController controller =
                new DirectConnectUiController(
                        service,
                        ClientMessages.forLanguage(ClientLanguage.ENGLISH),
                        action -> {
                            callbackSources.add(Thread.currentThread());
                            uiTasks.add(action);
                        },
                        ignored -> {
                            if (Thread.currentThread() != uiOwner) {
                                observerLeftUiOwner.set(true);
                            }
                        },
                        () -> {});

        try {
            controller.open();
            replaceEndpoint(controller, "127.0.0.1:" + reliablePort);
            controller.moveFocus(1);
            controller.moveFocus(1);
            controller.activate();

            pumpUntil(uiTasks, controller, DirectConnectUiPhase.CONFIRMING_IDENTITY);
            assertTrue(controller.model().fingerprint().isPresent());
            controller.activate();

            pumpUntil(uiTasks, controller, DirectConnectUiPhase.CONNECTED);
            assertEquals("player_one", controller.model().handleText());
            assertTrue(
                    controller.model().members().stream()
                            .anyMatch(member -> member.handle().value().equals("player_one")));
            assertFalse(callbackSources.isEmpty());
            assertTrue(callbackSources.stream().noneMatch(source -> source == uiOwner));
            assertFalse(observerLeftUiOwner.get());

            controller.escape();
            assertEquals(DirectConnectUiPhase.DISCONNECTED, controller.model().phase());
        } finally {
            controller.close();
        }

        assertEquals(
                ServerLauncher.EXIT_OK,
                launcherResult.get(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static void replaceEndpoint(DirectConnectUiController controller, String endpoint) {
        for (int index = 0; index < DirectConnectUiController.DEFAULT_ENDPOINT.length(); index++) {
            controller.backspace();
        }
        for (char character : endpoint.toCharArray()) {
            controller.appendCharacter(character);
        }
    }

    private static void pumpUntil(
            BlockingQueue<Runnable> uiTasks,
            DirectConnectUiController controller,
            DirectConnectUiPhase expected)
            throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(NETWORK_TIMEOUT_SECONDS);
        while (controller.model().phase() != expected) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new TimeoutException("UI did not reach " + expected);
            }
            Runnable task = uiTasks.poll(remaining, TimeUnit.NANOSECONDS);
            if (task == null) {
                throw new TimeoutException("UI callback queue did not reach " + expected);
            }
            task.run();
        }
    }

    private CompletableFuture<Integer> startServer(ProcessConfiguration process) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("direct-connect-ui-server-launcher")
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
        Path server = temporaryDirectory.resolve("ui-server.properties");
        Files.writeString(
                server,
                "server.name=Direct Connect UI Arena\n"
                        + "server.tick-rate=60\n"
                        + "server.reliable-port="
                        + reliablePort
                        + "\n"
                        + "server.realtime-port="
                        + differentPort(reliablePort)
                        + "\n"
                        + "server.maximum-players=4\n");

        KeyPair registryRoot = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path trustRoots = temporaryDirectory.resolve("ui-registry-roots.hex");
        Files.writeString(
                trustRoots, HexFormat.of().formatHex(registryRoot.getPublic().getEncoded()) + "\n");
        Path identity = temporaryDirectory.resolve("ui-identity.properties");
        Files.writeString(
                identity,
                "identity.sqlite-path=ui-identity.sqlite\n"
                        + "identity.registry-bundle-path=ui-registry.sfrb\n"
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path="
                        + trustRoots.getFileName()
                        + "\n"
                        + "identity.registry.refresh-source=LOCAL_BUNDLE\n");

        ServerTlsTestCertificateMaterial material =
                ServerTlsTestCertificateMaterial.create(reliablePort);
        Path privateKey = temporaryDirectory.resolve("ui-server-key.pk8");
        Path certificate = temporaryDirectory.resolve("ui-server-certificate.der");
        Files.write(privateKey, material.keyPair().getPrivate().getEncoded());
        Files.write(certificate, material.certificateDer());
        Path tls = temporaryDirectory.resolve("ui-tls.properties");
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
