package pl.grzegorz2047.standalonethewalls.server.config.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalIdentityProcessScheduleConfigurationTest {
    @TempDir Path temporaryDirectory;

    @BeforeEach
    void writeTrustRoot() throws IOException, NoSuchAlgorithmException {
        byte[] publicKey =
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
        Files.writeString(
                temporaryDirectory.resolve("roots.hex"),
                HexFormat.of().formatHex(publicKey) + '\n',
                StandardCharsets.UTF_8);
    }

    @Test
    void httpsScheduleDefaultsToDisabledBoundedValues() throws Exception {
        LocalIdentityProcessConfiguration configuration = load(httpsPrefix());
        RegistryRefreshConfiguration.Https https =
                (RegistryRefreshConfiguration.Https) configuration.registryRefreshConfiguration();

        assertThat(https.schedule()).isEqualTo(RegistryRefreshScheduleConfiguration.DEFAULT);
        assertThat(https.schedule().enabled()).isFalse();
    }

    @Test
    void loadsExplicitEnabledScheduleInSeconds() throws Exception {
        LocalIdentityProcessConfiguration configuration =
                load(
                        httpsPrefix()
                                + "identity.registry.scheduler.enabled=true\n"
                                + "identity.registry.scheduler.initial-delay-seconds=0\n"
                                + "identity.registry.scheduler.success-interval-seconds=120\n"
                                + "identity.registry.scheduler.initial-failure-backoff-seconds=3\n"
                                + "identity.registry.scheduler.maximum-failure-backoff-seconds=48\n"
                                + "identity.registry.scheduler.maximum-jitter-seconds=2\n");
        RegistryRefreshConfiguration.Https https =
                (RegistryRefreshConfiguration.Https) configuration.registryRefreshConfiguration();

        assertThat(https.schedule())
                .isEqualTo(
                        new RegistryRefreshScheduleConfiguration(
                                true,
                                Duration.ZERO,
                                Duration.ofMinutes(2),
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(48),
                                Duration.ofSeconds(2)));
    }

    @Test
    void localBundleRejectsEverySchedulerKeyInsteadOfIgnoringIt() {
        assertRejected(localPrefix() + "identity.registry.scheduler.enabled=false\n");
        assertRejected(localPrefix() + "identity.registry.scheduler.initial-delay-seconds=60\n");
        assertRejected(localPrefix() + "identity.registry.scheduler.maximum-jitter-seconds=5\n");
    }

    @Test
    void rejectsInvalidBooleanZeroNegativeBackoffOrderingAndUnitOverflow() {
        assertRejected(httpsPrefix() + "identity.registry.scheduler.enabled=TRUE\n");
        assertRejected(httpsPrefix() + "identity.registry.scheduler.success-interval-seconds=0\n");
        assertRejected(
                httpsPrefix() + "identity.registry.scheduler.initial-failure-backoff-seconds=0\n");
        assertRejected(
                httpsPrefix() + "identity.registry.scheduler.maximum-failure-backoff-seconds=0\n");
        assertRejected(httpsPrefix() + "identity.registry.scheduler.initial-delay-seconds=-1\n");
        assertRejected(
                httpsPrefix()
                        + "identity.registry.scheduler.initial-failure-backoff-seconds=10\n"
                        + "identity.registry.scheduler.maximum-failure-backoff-seconds=9\n");
        assertRejected(httpsPrefix() + "identity.registry.scheduler.maximum-jitter-seconds=3601\n");
        assertRejected(
                httpsPrefix()
                        + "identity.registry.scheduler.success-interval-seconds=9223372036854775807\n");
    }

    @Test
    void directConfigurationRejectsOverflowAndUnsafeRanges() {
        assertThatThrownBy(
                        () ->
                                new RegistryRefreshScheduleConfiguration(
                                        true,
                                        Duration.ZERO,
                                        Duration.ofNanos(Long.MAX_VALUE),
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(1),
                                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistryRefreshScheduleConfiguration(
                                        true,
                                        Duration.ZERO,
                                        Duration.ofSeconds(1),
                                        Duration.ofMillis(999),
                                        Duration.ofSeconds(1),
                                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LocalIdentityProcessConfiguration load(String content) throws Exception {
        Path path = temporaryDirectory.resolve("identity.properties");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return LocalIdentityProcessConfigurationLoader.load(path);
    }

    private void assertRejected(String content) {
        assertThatThrownBy(() -> load(content)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String localPrefix() {
        return commonPrefix() + "identity.registry.refresh-source=LOCAL_BUNDLE\n";
    }

    private static String httpsPrefix() {
        return commonPrefix()
                + "identity.registry.refresh-source=HTTPS\n"
                + "identity.registry.https.json-uri=https://registry.example/v1/registry.json\n"
                + "identity.registry.https.digest-uri=https://registry.example/v1/registry.sha256\n"
                + "identity.registry.https.signature-uri=https://registry.example/v1/registry.sig\n";
    }

    private static String commonPrefix() {
        return "identity.sqlite-path=identity.sqlite\n"
                + "identity.registry-bundle-path=registry.sfrb\n"
                + "identity.authorization-mode=LOCAL_TOFU\n"
                + "identity.trust-roots-path=roots.hex\n";
    }
}
