package pl.grzegorz2047.standalonethewalls.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLauncherIdentityTest {
    @TempDir Path temporaryDirectory;

    @Test
    void existingSmokeInvocationWithoutIdentityRemainsSupported() {
        assertThat(ServerLauncher.run(new String[] {"--run-for-ticks", "1"}))
                .isEqualTo(ServerLauncher.EXIT_OK);
    }

    @Test
    void identityOptionIsStrictlySingleAndRequiresAValue() {
        assertThat(
                        ServerLauncher.run(
                                new String[] {
                                    "--identity-config", "one.properties",
                                    "--identity-config", "two.properties",
                                    "--validate-config"
                                }))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(ServerLauncher.run(new String[] {"--identity-config"}))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(ServerLauncher.run(new String[] {"--identity-config", "--validate-config"}))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(ServerLauncher.run(new String[] {"--unknown"}))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(ServerLauncher.run(new String[] {"--validate-config", "--run-for-ticks", "1"}))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
    }

    @Test
    void validateOnlyChecksIdentityAndTrustRootsWithoutCreatingSqlite()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("validate");

        int exitCode =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config",
                            files.configuration().toString(),
                            "--validate-config"
                        });

        assertThat(exitCode).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(files.sqlite()).doesNotExist();
        assertThat(files.bundle()).doesNotExist();
    }

    @Test
    void invalidTrustRootFailsValidationWithoutCreatingSqlite() throws IOException {
        Path configuration = temporaryDirectory.resolve("invalid-trust.properties");
        Path sqlite = temporaryDirectory.resolve("invalid-trust.sqlite");
        Files.writeString(
                temporaryDirectory.resolve("invalid-trust-roots.hex"),
                "not-a-public-key\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                configuration,
                configuration(fileName(sqlite), "invalid-trust.sfrb", "invalid-trust-roots.hex"),
                StandardCharsets.UTF_8);

        assertThat(
                        ServerLauncher.run(
                                new String[] {
                                    "--identity-config",
                                    configuration.toString(),
                                    "--validate-config"
                                }))
                .isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(sqlite).doesNotExist();
    }

    @Test
    void smokeRunOpensLocalIdentityBeforeTickLoopAndCreatesSqlite()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("smoke");

        int exitCode =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config",
                            files.configuration().toString(),
                            "--run-for-ticks",
                            "1"
                        });

        assertThat(exitCode).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(files.sqlite()).isRegularFile();
        assertThat(files.bundle()).doesNotExist();
    }

    @Test
    void directoryAsSqliteTargetBlocksProcessBeforeTickLoop()
            throws IOException, NoSuchAlgorithmException {
        ProcessFiles files = writeValidIdentityConfiguration("directory");
        Files.createDirectory(files.sqlite());

        int exitCode =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config",
                            files.configuration().toString(),
                            "--run-for-ticks",
                            "1"
                        });

        assertThat(exitCode).isEqualTo(ServerLauncher.EXIT_USAGE_OR_CONFIGURATION);
        assertThat(files.bundle()).doesNotExist();
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
                configuration(fileName(sqlite), fileName(bundle), fileName(roots)),
                StandardCharsets.UTF_8);
        return new ProcessFiles(configuration, sqlite, bundle);
    }

    private static String fileName(Path path) {
        return Objects.requireNonNull(path.getFileName(), "path file name").toString();
    }

    private static String configuration(String sqlite, String bundle, String roots) {
        return "identity.sqlite-path="
                + sqlite
                + '\n'
                + "identity.registry-bundle-path="
                + bundle
                + '\n'
                + "identity.authorization-mode=LOCAL_TOFU\n"
                + "identity.trust-roots-path="
                + roots
                + '\n';
    }

    private record ProcessFiles(Path configuration, Path sqlite, Path bundle) {}
}
