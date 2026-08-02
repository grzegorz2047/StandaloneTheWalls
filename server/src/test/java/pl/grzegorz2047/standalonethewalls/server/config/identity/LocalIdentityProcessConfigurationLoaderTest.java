package pl.grzegorz2047.standalonethewalls.server.config.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;

class LocalIdentityProcessConfigurationLoaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsRelativePathsExactDefaultPolicyAndLocalRefresh()
            throws IOException, NoSuchAlgorithmException, RegistrySnapshotException {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("configuration"));
        KeyPair root = root();
        writeTrustRoots(directory.resolve("roots.hex"), root.getPublic().getEncoded());
        Path configurationPath =
                writeConfiguration(
                        directory,
                        "identity.sqlite-path=data/identity.sqlite\n"
                                + "identity.registry-bundle-path=cache/registry.sfrb\n"
                                + "identity.authorization-mode=HYBRID\n"
                                + "identity.trust-roots-path=roots.hex\n"
                                + "identity.registry.refresh-source=LOCAL_BUNDLE\n");

        LocalIdentityProcessConfiguration configuration =
                LocalIdentityProcessConfigurationLoader.load(configurationPath);

        assertThat(configuration.runtimeConfiguration().sqliteDatabasePath())
                .isEqualTo(directory.resolve("data/identity.sqlite").toAbsolutePath().normalize());
        assertThat(configuration.runtimeConfiguration().registryBundlePath())
                .isEqualTo(directory.resolve("cache/registry.sfrb").toAbsolutePath().normalize());
        assertThat(configuration.runtimeConfiguration().authorizationMode())
                .isEqualTo(HandleAuthorizationMode.HYBRID);
        assertThat(configuration.trustRootsPath())
                .isEqualTo(directory.resolve("roots.hex").toAbsolutePath().normalize());
        assertThat(configuration.trustBundle().size()).isOne();
        assertThat(configuration.registryPolicy()).isEqualTo(RegistrySnapshotPolicy.DEFAULT);
        assertThat(configuration.registryRefreshConfiguration())
                .isInstanceOf(RegistryRefreshConfiguration.LocalBundle.class);
    }

    @Test
    void loadsEveryBoundedPolicyOverride()
            throws IOException, NoSuchAlgorithmException, RegistrySnapshotException {
        KeyPair root = root();
        writeTrustRoots(temporaryDirectory.resolve("roots.hex"), root.getPublic().getEncoded());
        Path configurationPath =
                writeConfiguration(
                        temporaryDirectory,
                        "identity.sqlite-path=identity.sqlite\n"
                                + "identity.registry-bundle-path=registry.sfrb\n"
                                + "identity.authorization-mode=GLOBAL_ONLY\n"
                                + "identity.trust-roots-path=roots.hex\n"
                                + "identity.registry.refresh-source=LOCAL_BUNDLE\n"
                                + "identity.registry.minimum-sequence=42\n"
                                + "identity.registry.maximum-age-seconds=3600\n"
                                + "identity.registry.maximum-future-skew-seconds=30\n"
                                + "identity.registry.maximum-json-bytes=1048576\n"
                                + "identity.registry.maximum-entries=2500\n");

        LocalIdentityProcessConfiguration configuration =
                LocalIdentityProcessConfigurationLoader.load(configurationPath);

        assertThat(configuration.registryPolicy())
                .isEqualTo(
                        new RegistrySnapshotPolicy(
                                42L,
                                Duration.ofHours(1),
                                Duration.ofSeconds(30),
                                1_048_576,
                                2_500));
    }

    @Test
    void loadsHttpsRefreshUsingJsonPolicyBoundAndExplicitTimeouts()
            throws IOException, NoSuchAlgorithmException, RegistrySnapshotException {
        KeyPair root = root();
        writeTrustRoots(temporaryDirectory.resolve("roots.hex"), root.getPublic().getEncoded());
        Path configurationPath =
                writeConfiguration(
                        temporaryDirectory,
                        "identity.sqlite-path=identity.sqlite\n"
                                + "identity.registry-bundle-path=registry.sfrb\n"
                                + "identity.authorization-mode=GLOBAL_ONLY\n"
                                + "identity.trust-roots-path=roots.hex\n"
                                + "identity.registry.refresh-source=HTTPS\n"
                                + "identity.registry.maximum-json-bytes=4096\n"
                                + "identity.registry.https.json-uri=https://registry.example/releases/v7/registry.json\n"
                                + "identity.registry.https.digest-uri=https://registry.example/releases/v7/registry.sha256\n"
                                + "identity.registry.https.signature-uri=https://registry.example/releases/v7/registry.sig\n"
                                + "identity.registry.https.connect-timeout-seconds=7\n"
                                + "identity.registry.https.request-timeout-seconds=19\n");

        LocalIdentityProcessConfiguration configuration =
                LocalIdentityProcessConfigurationLoader.load(configurationPath);

        assertThat(configuration.registryRefreshConfiguration())
                .isInstanceOfSatisfying(
                        RegistryRefreshConfiguration.Https.class,
                        https -> {
                            RegistrySnapshotHttpsConfiguration remote = https.configuration();
                            assertThat(remote.canonicalJsonUri())
                                    .isEqualTo(
                                            URI.create(
                                                    "https://registry.example/releases/v7/registry.json"));
                            assertThat(remote.digestUri())
                                    .isEqualTo(
                                            URI.create(
                                                    "https://registry.example/releases/v7/registry.sha256"));
                            assertThat(remote.signatureUri())
                                    .isEqualTo(
                                            URI.create(
                                                    "https://registry.example/releases/v7/registry.sig"));
                            assertThat(remote.connectTimeout()).isEqualTo(Duration.ofSeconds(7));
                            assertThat(remote.requestTimeout()).isEqualTo(Duration.ofSeconds(19));
                            assertThat(remote.maximumJsonBytes()).isEqualTo(4096);
                        });
    }

    @Test
    void rejectsDuplicateUnknownMissingAndMalformedProperties()
            throws IOException, NoSuchAlgorithmException {
        KeyPair root = root();
        writeTrustRoots(temporaryDirectory.resolve("roots.hex"), root.getPublic().getEncoded());

        assertRejected(validPrefix() + "identity.authorization-mode=LOCAL_TOFU\n");
        assertRejected(validPrefix() + "identity.unknown=value\n");
        assertRejected(
                "identity.sqlite-path=identity.sqlite\n"
                        + "identity.registry-bundle-path=registry.sfrb\n"
                        + "identity.trust-roots-path=roots.hex\n"
                        + "identity.registry.refresh-source=LOCAL_BUNDLE\n");
        assertRejected(validPrefix().replace("LOCAL_TOFU", "local-tofu"));
        assertRejected(validPrefix() + "identity.registry.minimum-sequence=-1\n");
        assertRejected(validPrefix() + "identity.registry.maximum-json-bytes=1MB\n");
        assertRejected(validPrefix().replace("identity.sqlite-path", " identity.sqlite-path"));
        assertRejected(validPrefix().replace("LOCAL_BUNDLE", "local-bundle"));
        assertRejected(
                validPrefix().replace("identity.registry.refresh-source=LOCAL_BUNDLE\n", ""));
    }

    @Test
    void enforcesSourceSpecificHttpsKeys() throws IOException, NoSuchAlgorithmException {
        KeyPair root = root();
        writeTrustRoots(temporaryDirectory.resolve("roots.hex"), root.getPublic().getEncoded());

        assertRejected(
                validPrefix()
                        + "identity.registry.https.json-uri=https://registry.example/registry.json\n");
        assertRejected(
                httpsPrefix()
                        + "identity.registry.https.json-uri=https://registry.example/registry.json\n"
                        + "identity.registry.https.digest-uri=https://registry.example/registry.sha256\n");
        assertRejected(
                httpsPrefix()
                        + "identity.registry.https.json-uri=http://registry.example/registry.json\n"
                        + "identity.registry.https.digest-uri=https://registry.example/registry.sha256\n"
                        + "identity.registry.https.signature-uri=https://registry.example/registry.sig\n");
        assertRejected(
                httpsPrefix()
                        + "identity.registry.https.json-uri=https://registry.example/registry.json\n"
                        + "identity.registry.https.digest-uri=https://registry.example/registry.sha256\n"
                        + "identity.registry.https.signature-uri=https://registry.example/registry.sig\n"
                        + "identity.registry.https.connect-timeout-seconds=0\n");
    }

    @Test
    void rejectsSharedTrustStateAndRuntimePaths() throws IOException, NoSuchAlgorithmException {
        KeyPair root = root();
        writeTrustRoots(temporaryDirectory.resolve("registry.sfrb"), root.getPublic().getEncoded());
        String configuration =
                "identity.sqlite-path=identity.sqlite\n"
                        + "identity.registry-bundle-path=registry.sfrb\n"
                        + "identity.authorization-mode=LOCAL_TOFU\n"
                        + "identity.trust-roots-path=registry.sfrb\n"
                        + "identity.registry.refresh-source=LOCAL_BUNDLE\n";

        assertRejected(configuration);
    }

    @Test
    void trustRootsRejectUppercaseDuplicatesPrivateKeysBlankLinesAndDirectories()
            throws IOException, NoSuchAlgorithmException {
        KeyPair root = root();
        String publicHex = hex(root.getPublic().getEncoded());
        String uppercase = publicHex.toUpperCase(java.util.Locale.ROOT);
        Path uppercasePath = temporaryDirectory.resolve("uppercase.hex");
        Files.writeString(uppercasePath, uppercase + '\n', StandardCharsets.UTF_8);

        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(uppercasePath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(uppercase);

        Path duplicatePath = temporaryDirectory.resolve("duplicate.hex");
        Files.writeString(
                duplicatePath, publicHex + '\n' + publicHex + '\n', StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(duplicatePath))
                .isInstanceOf(IllegalArgumentException.class);

        Path privatePath = temporaryDirectory.resolve("private.hex");
        Files.writeString(
                privatePath, hex(root.getPrivate().getEncoded()) + '\n', StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(privatePath))
                .isInstanceOf(RegistrySnapshotException.class);

        Path blankPath = temporaryDirectory.resolve("blank.hex");
        Files.writeString(blankPath, publicHex + "\n\n" + publicHex, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(blankPath))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityAndTrustFilesRejectSymlinksAndOversizedInput()
            throws IOException, NoSuchAlgorithmException {
        KeyPair root = root();
        Path roots = temporaryDirectory.resolve("roots.hex");
        writeTrustRoots(roots, root.getPublic().getEncoded());
        Path realConfiguration = writeConfiguration(temporaryDirectory, validPrefix());
        Path configurationLink = temporaryDirectory.resolve("identity-link.properties");
        Files.createSymbolicLink(configurationLink, realConfiguration.getFileName());
        Path rootsLink = temporaryDirectory.resolve("roots-link.hex");
        Files.createSymbolicLink(rootsLink, roots.getFileName());

        assertThatThrownBy(() -> LocalIdentityProcessConfigurationLoader.load(configurationLink))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistryTrustBundleFileLoader.load(rootsLink))
                .isInstanceOf(IllegalArgumentException.class);

        Path oversized = temporaryDirectory.resolve("oversized.properties");
        Files.writeString(
                oversized,
                "x".repeat(LocalIdentityProcessConfigurationLoader.MAXIMUM_FILE_BYTES + 1),
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> LocalIdentityProcessConfigurationLoader.load(oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejected(String content) throws IOException {
        Path path = writeConfiguration(temporaryDirectory, content);
        assertThatThrownBy(() -> LocalIdentityProcessConfigurationLoader.load(path))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String validPrefix() {
        return "identity.sqlite-path=identity.sqlite\n"
                + "identity.registry-bundle-path=registry.sfrb\n"
                + "identity.authorization-mode=LOCAL_TOFU\n"
                + "identity.trust-roots-path=roots.hex\n"
                + "identity.registry.refresh-source=LOCAL_BUNDLE\n";
    }

    private static String httpsPrefix() {
        return "identity.sqlite-path=identity.sqlite\n"
                + "identity.registry-bundle-path=registry.sfrb\n"
                + "identity.authorization-mode=LOCAL_TOFU\n"
                + "identity.trust-roots-path=roots.hex\n"
                + "identity.registry.refresh-source=HTTPS\n";
    }

    private static Path writeConfiguration(Path directory, String content) throws IOException {
        Path path = directory.resolve("identity.properties");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void writeTrustRoots(Path path, byte[]... roots) throws IOException {
        StringBuilder content = new StringBuilder();
        for (byte[] root : roots) {
            content.append(hex(root)).append('\n');
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    private static KeyPair root() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }
}
