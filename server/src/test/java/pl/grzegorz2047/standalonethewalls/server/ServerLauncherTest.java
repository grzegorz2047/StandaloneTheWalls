package pl.grzegorz2047.standalonethewalls.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.server.testsupport.ServerTlsTestCertificateMaterial;

class ServerLauncherTest {
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
    void validatesTlsCredentialsWithoutBindingAndRunsBoundedTlsLifecycle() throws Exception {
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

    private ProcessConfiguration createProcessConfiguration(int reliablePort) throws Exception {
        Path server = temporaryDirectory.resolve("server-" + reliablePort + ".properties");
        Files.writeString(
                server,
                "server.name=TLS Test Arena\n"
                        + "server.tick-rate=20\n"
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
        Files.write(certificate, material.certificate().getEncoded());
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
        return new ProcessConfiguration(server, identity, tls);
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
