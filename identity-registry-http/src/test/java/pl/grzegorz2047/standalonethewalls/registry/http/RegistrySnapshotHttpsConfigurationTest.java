package pl.grzegorz2047.standalonethewalls.registry.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;

class RegistrySnapshotHttpsConfigurationTest {
    private static final URI JSON = URI.create("https://registry.example/releases/v7/registry-v1.json");
    private static final URI DIGEST =
            URI.create("https://registry.example/releases/v7/registry-v1.sha256");
    private static final URI SIGNATURE =
            URI.create("https://registry.example/releases/v7/registry-v1.sig");

    @Test
    void acceptsExplicitVersionedHttpsResourcesAndBoundedTimeouts() {
        RegistrySnapshotHttpsConfiguration configuration =
                new RegistrySnapshotHttpsConfiguration(
                        JSON,
                        DIGEST,
                        SIGNATURE,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(20),
                        4096);

        assertThat(configuration.canonicalJsonUri()).isEqualTo(JSON);
        assertThat(configuration.digestUri()).isEqualTo(DIGEST);
        assertThat(configuration.signatureUri()).isEqualTo(SIGNATURE);
        assertThat(configuration.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(configuration.requestTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(configuration.maximumJsonBytes()).isEqualTo(4096);
    }

    @Test
    void rejectsNonHttpsRelativeUserInfoFragmentAndMissingHostUris() {
        assertInvalidJsonUri(URI.create("http://registry.example/registry.json"));
        assertInvalidJsonUri(URI.create("/registry.json"));
        assertInvalidJsonUri(URI.create("https://user@registry.example/registry.json"));
        assertInvalidJsonUri(URI.create("https://registry.example/registry.json#fragment"));
        assertInvalidJsonUri(URI.create("https:/registry.json"));
    }

    @Test
    void rejectsResourcesThatNormalizeToTheSameUri() {
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        URI.create("https://REGISTRY.example/releases/./v7/data"),
                                        URI.create("https://registry.example/releases/v7/data"),
                                        SIGNATURE,
                                        4096))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
    }

    @Test
    void rejectsTimeoutsAndJsonBoundsOutsideSafeRanges() {
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        JSON,
                                        DIGEST,
                                        SIGNATURE,
                                        Duration.ZERO,
                                        Duration.ofSeconds(1),
                                        4096))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        JSON,
                                        DIGEST,
                                        SIGNATURE,
                                        Duration.ofSeconds(1),
                                        RegistrySnapshotHttpsConfiguration.MAXIMUM_TIMEOUT
                                                .plusMillis(1),
                                        4096))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        JSON, DIGEST, SIGNATURE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        JSON,
                                        DIGEST,
                                        SIGNATURE,
                                        RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInvalidJsonUri(URI uri) {
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotHttpsConfiguration(
                                        uri, DIGEST, SIGNATURE, 4096))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }
}
