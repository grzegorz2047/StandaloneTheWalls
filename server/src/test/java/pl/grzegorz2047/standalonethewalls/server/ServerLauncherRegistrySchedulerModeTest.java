package pl.grzegorz2047.standalonethewalls.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLauncherRegistrySchedulerModeTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validateOnlyParsesEnabledSchedulerWithoutStartingRuntimeOrNetwork()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeHttpsConfiguration("validate-scheduler");

        int exit =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config",
                            files.configuration().toString(),
                            "--validate-config"
                        });

        assertThat(exit).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(files.sqlite()).doesNotExist();
        assertThat(files.bundle()).doesNotExist();
        assertNoRegistryRefreshThread();
    }

    @Test
    void oneShotIdentityCommandNeverStartsAutomaticSchedulerOrNetwork()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeHttpsConfiguration("one-shot-scheduler");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        int exit =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config",
                            files.configuration().toString(),
                            "--identity-command",
                            "identity",
                            "list",
                            "handles"
                        },
                        new PrintStream(bytes, true, StandardCharsets.UTF_8));

        assertThat(exit).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(bytes.toString(StandardCharsets.UTF_8)).startsWith("response=HANDLES_LISTED");
        assertThat(files.sqlite()).isRegularFile();
        assertThat(files.bundle()).doesNotExist();
        assertNoRegistryRefreshThread();
    }

    private ProcessFiles writeHttpsConfiguration(String prefix)
            throws IOException, NoSuchAlgorithmException {
        Path sqlite = temporaryDirectory.resolve(prefix + ".sqlite");
        Path bundle = temporaryDirectory.resolve(prefix + ".sfrb");
        Path roots = temporaryDirectory.resolve(prefix + "-roots.hex");
        Path configuration = temporaryDirectory.resolve(prefix + ".properties");
        byte[] publicKey =
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
        Files.writeString(
                roots, HexFormat.of().formatHex(publicKey) + '\n', StandardCharsets.UTF_8);
        Files.writeString(
                configuration,
                "identity.sqlite-path="
                        + sqlite.getFileName()
                        + '\n'
                        + "identity.registry-bundle-path="
                        + bundle.getFileName()
                        + '\n'
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path="
                        + roots.getFileName()
                        + '\n'
                        + "identity.registry.refresh-source=HTTPS\n"
                        + "identity.registry.https.json-uri=https://127.0.0.1:1/v1/registry.json\n"
                        + "identity.registry.https.digest-uri=https://127.0.0.1:1/v1/registry.sha256\n"
                        + "identity.registry.https.signature-uri=https://127.0.0.1:1/v1/registry.sig\n"
                        + "identity.registry.https.connect-timeout-seconds=1\n"
                        + "identity.registry.https.request-timeout-seconds=1\n"
                        + "identity.registry.scheduler.enabled=true\n"
                        + "identity.registry.scheduler.initial-delay-seconds=0\n",
                StandardCharsets.UTF_8);
        return new ProcessFiles(configuration, sqlite, bundle);
    }

    private static void assertNoRegistryRefreshThread() {
        assertThat(
                        Thread.getAllStackTraces().keySet().stream()
                                .filter(Thread::isAlive)
                                .map(Thread::getName))
                .doesNotContain("sunderfront-registry-refresh");
    }

    private record ProcessFiles(Path configuration, Path sqlite, Path bundle) {}
}
