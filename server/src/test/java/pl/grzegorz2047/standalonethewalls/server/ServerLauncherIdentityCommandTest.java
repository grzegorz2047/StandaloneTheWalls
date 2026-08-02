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
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLauncherIdentityCommandTest {
    private static final String FIRST_PLAYER = "sf1_" + "a".repeat(52);
    private static final String SECOND_PLAYER = "sf1_" + "b".repeat(52);

    @TempDir Path temporaryDirectory;

    @Test
    void requiresIdentityConfigurationAndRejectsConflictingModes() {
        CommandRun missingConfiguration = run("--identity-command", "identity", "list", "handles");
        CommandRun missingTokens = run("--identity-command");
        CommandRun validationConflict =
                run(
                        "--identity-config",
                        "identity.properties",
                        "--validate-config",
                        "--identity-command",
                        "identity",
                        "list",
                        "handles");
        CommandRun smokeConflict =
                run(
                        "--identity-config",
                        "identity.properties",
                        "--run-for-ticks",
                        "1",
                        "--identity-command",
                        "identity",
                        "list",
                        "handles");

        assertThat(missingConfiguration.exitCode())
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(missingTokens.exitCode()).isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(validationConflict.exitCode())
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(smokeConflict.exitCode()).isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(missingConfiguration.lines()).isEmpty();
        assertThat(missingTokens.lines()).isEmpty();
        assertThat(validationConflict.lines()).isEmpty();
        assertThat(smokeConflict.lines()).isEmpty();
    }

    @Test
    void mutationPersistsAcrossOneShotRuntimeReopen() throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("persistent-command");

        CommandRun reserve =
                run(
                        "--identity-config",
                        files.configuration().toString(),
                        "--identity-command",
                        "identity",
                        "reserve",
                        "alpha",
                        FIRST_PLAYER,
                        "Manual local review");
        CommandRun inspect =
                run(
                        "--identity-config",
                        files.configuration().toString(),
                        "--identity-command",
                        "identity",
                        "inspect",
                        "handle",
                        "alpha");
        CommandRun conflict =
                run(
                        "--identity-config",
                        files.configuration().toString(),
                        "--identity-command",
                        "identity",
                        "reserve",
                        "alpha",
                        SECOND_PLAYER,
                        "Conflicting local claim");

        assertThat(reserve.exitCode()).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(reserve.lines())
                .containsExactly("response=HANDLE_MUTATION_COMPLETED", "result=RESERVED");
        assertThat(inspect.exitCode()).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(inspect.lines())
                .containsExactly(
                        "response=HANDLE_INSPECTED",
                        "handle=alpha",
                        "found=true",
                        "playerId=" + FIRST_PLAYER);
        assertThat(conflict.exitCode()).isEqualTo(ServerLauncher.EXIT_ADMINISTRATION_REJECTED);
        assertThat(conflict.lines())
                .containsExactly("response=HANDLE_MUTATION_COMPLETED", "result=CONFLICT");
        assertThat(files.sqlite()).isRegularFile();
        assertThat(files.bundle()).doesNotExist();
    }

    @Test
    void providerFailureUsesAdministrationRejectedExitCode()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("provider-failure");

        CommandRun result =
                run(
                        "--identity-config",
                        files.configuration().toString(),
                        "--identity-command",
                        "identity",
                        "verify-snapshot");

        assertThat(result.exitCode()).isEqualTo(ServerLauncher.EXIT_ADMINISTRATION_REJECTED);
        assertThat(result.lines())
                .containsExactly(
                        "response=REGISTRY_OPERATION_COMPLETED", "result=PROVIDER_FAILURE");
        assertThat(files.sqlite()).isRegularFile();
        assertThat(files.bundle()).doesNotExist();
    }

    @Test
    void terminalCommandTokensAreParsedOnlyByIdentityCommandParser()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("terminal-token");

        CommandRun result =
                run(
                        "--identity-config",
                        files.configuration().toString(),
                        "--identity-command",
                        "identity",
                        "inspect",
                        "handle",
                        "--validate-config");

        assertThat(result.exitCode()).isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(result.lines()).isEmpty();
    }

    private CommandRun run(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exitCode = ServerLauncher.run(arguments, output);
        }
        List<String> lines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        return new CommandRun(exitCode, lines);
    }

    private ProcessFiles writeValidIdentityConfiguration(String prefix)
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
                        + fileName(sqlite)
                        + '\n'
                        + "identity.registry-bundle-path="
                        + fileName(bundle)
                        + '\n'
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path="
                        + fileName(roots)
                        + '\n',
                StandardCharsets.UTF_8);
        return new ProcessFiles(configuration, sqlite, bundle);
    }

    private static String fileName(Path path) {
        return Objects.requireNonNull(path.getFileName(), "path file name").toString();
    }

    private record CommandRun(int exitCode, List<String> lines) {
        private CommandRun {
            lines = List.copyOf(lines);
        }
    }

    private record ProcessFiles(Path configuration, Path sqlite, Path bundle) {}
}
