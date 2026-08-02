package pl.grzegorz2047.standalonethewalls.registry.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;

class RegistrySnapshotHttpsProviderTest {
    private static final URI JSON = URI.create("https://registry.example/releases/v7/registry-v1.json");
    private static final URI DIGEST =
            URI.create("https://registry.example/releases/v7/registry-v1.sha256");
    private static final URI SIGNATURE =
            URI.create("https://registry.example/releases/v7/registry-v1.sig");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final byte[] JSON_BYTES = "{\"schemaVersion\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] DIGEST_LINE = ascii("00".repeat(32) + "\n");
    private static final byte[] SIGNATURE_LINE = ascii("11".repeat(64) + "\n");

    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    @Test
    void loadsThreeExplicitResourcesAsOneUntrustedArtifact() throws RegistrySnapshotProviderException {
        ScriptedClient client =
                new ScriptedClient(
                        response(200, JSON, Map.of("Content-Length", List.of("19")), JSON_BYTES),
                        response(200, DIGEST, Map.of(), DIGEST_LINE),
                        response(200, SIGNATURE, Map.of("Content-Encoding", List.of("identity")), SIGNATURE_LINE));
        RegistrySnapshotHttpsProvider provider = new RegistrySnapshotHttpsProvider(configuration(1024), client);

        RegistrySnapshotArtifact artifact = provider.load();

        assertThat(artifact.canonicalJson()).containsExactly(JSON_BYTES);
        assertThat(artifact.digest()).containsOnly(0);
        assertThat(artifact.signature()).containsOnly(0x11);
        assertThat(client.requests())
                .containsExactly(
                        new RequestedResource(JSON, REQUEST_TIMEOUT),
                        new RequestedResource(DIGEST, REQUEST_TIMEOUT),
                        new RequestedResource(SIGNATURE, REQUEST_TIMEOUT));
    }

    @Test
    void rejectsNonSuccessStatusHttpsDowngradeAndCompressedResponse() {
        assertProviderFailure(
                new ScriptedClient(response(503, JSON, Map.of(), JSON_BYTES)),
                configuration(1024));
        assertProviderFailure(
                new ScriptedClient(
                        response(
                                200,
                                URI.create("http://mirror.example/registry-v1.json"),
                                Map.of(),
                                JSON_BYTES)),
                configuration(1024));
        assertProviderFailure(
                new ScriptedClient(
                        response(
                                200,
                                JSON,
                                Map.of("Content-Encoding", List.of("gzip")),
                                JSON_BYTES)),
                configuration(1024));
    }

    @Test
    void enforcesDeclaredAndActualBodyLimitsAndAlwaysClosesBody() {
        CloseTrackingInputStream declaredOversize = new CloseTrackingInputStream(JSON_BYTES);
        ScriptedClient declaredClient =
                new ScriptedClient(
                        response(
                                200,
                                JSON,
                                Map.of("Content-Length", List.of("20")),
                                declaredOversize));

        assertProviderFailure(declaredClient, configuration(19));
        assertThat(declaredOversize.closed()).isTrue();

        CloseTrackingInputStream actualOversize =
                new CloseTrackingInputStream(new byte[] {1, 2, 3, 4, 5});
        ScriptedClient actualClient =
                new ScriptedClient(response(200, JSON, Map.of(), actualOversize));

        assertProviderFailure(actualClient, configuration(4));
        assertThat(actualOversize.closed()).isTrue();

        CloseTrackingInputStream mismatchedLength = new CloseTrackingInputStream(JSON_BYTES);
        ScriptedClient mismatchClient =
                new ScriptedClient(
                        response(
                                200,
                                JSON,
                                Map.of("Content-Length", List.of("18")),
                                mismatchedLength));

        assertProviderFailure(mismatchClient, configuration(1024));
        assertThat(mismatchedLength.closed()).isTrue();
    }

    @Test
    void requiresExactLowercaseHexLinesForDigestAndSignature() {
        ScriptedClient uppercaseDigest =
                new ScriptedClient(
                        response(200, JSON, Map.of(), JSON_BYTES),
                        response(200, DIGEST, Map.of(), ascii("AA".repeat(32) + "\n")));
        assertProviderFailure(uppercaseDigest, configuration(1024));

        ScriptedClient missingSignatureLf =
                new ScriptedClient(
                        response(200, JSON, Map.of(), JSON_BYTES),
                        response(200, DIGEST, Map.of(), DIGEST_LINE),
                        response(200, SIGNATURE, Map.of(), ascii("11".repeat(64))));
        assertProviderFailure(missingSignatureLf, configuration(1024));
    }

    @Test
    void mapsTransportFailureWithoutExposingResourceAndPreservesInterruption() {
        RegistrySnapshotHttpsProvider failed =
                new RegistrySnapshotHttpsProvider(
                        configuration(1024),
                        (uri, timeout) -> {
                            throw new IOException("secret upstream detail at " + uri);
                        });

        assertThatThrownBy(failed::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("HTTPS registry snapshot resources are unavailable or invalid")
                .hasMessageNotContaining("registry.example")
                .hasMessageNotContaining("secret upstream detail");

        RegistrySnapshotHttpsProvider interrupted =
                new RegistrySnapshotHttpsProvider(
                        configuration(1024),
                        (uri, timeout) -> {
                            throw new InterruptedException("interrupted transport");
                        });

        assertThatThrownBy(interrupted::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("HTTPS registry snapshot resources are unavailable or invalid");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static RegistrySnapshotHttpsConfiguration configuration(int maximumJsonBytes) {
        return new RegistrySnapshotHttpsConfiguration(
                JSON,
                DIGEST,
                SIGNATURE,
                Duration.ofSeconds(3),
                REQUEST_TIMEOUT,
                maximumJsonBytes);
    }

    private static void assertProviderFailure(
            RegistryHttpClient client, RegistrySnapshotHttpsConfiguration configuration) {
        RegistrySnapshotHttpsProvider provider =
                new RegistrySnapshotHttpsProvider(configuration, client);
        assertThatThrownBy(provider::load)
                .isInstanceOf(RegistrySnapshotProviderException.class)
                .hasMessage("HTTPS registry snapshot resources are unavailable or invalid");
    }

    private static byte[] ascii(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static HttpResponse<InputStream> response(
            int statusCode, URI finalUri, Map<String, List<String>> headers, byte[] body) {
        return response(statusCode, finalUri, headers, new ByteArrayInputStream(body));
    }

    private static HttpResponse<InputStream> response(
            int statusCode, URI finalUri, Map<String, List<String>> headers, InputStream body) {
        HttpHeaders httpHeaders = HttpHeaders.of(headers, (name, value) -> true);
        HttpRequest request = HttpRequest.newBuilder(JSON).GET().build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return httpHeaders;
            }

            @Override
            public InputStream body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return finalUri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class ScriptedClient implements RegistryHttpClient {
        private final Deque<HttpResponse<InputStream>> responses;
        private final List<RequestedResource> requests = new ArrayList<>();

        private ScriptedClient(HttpResponse<InputStream>... responses) {
            this.responses = new ArrayDeque<>(Arrays.asList(responses));
        }

        @Override
        public HttpResponse<InputStream> get(URI uri, Duration requestTimeout) throws IOException {
            requests.add(new RequestedResource(uri, requestTimeout));
            HttpResponse<InputStream> response = responses.pollFirst();
            if (response == null) {
                throw new IOException("scripted response missing");
            }
            return response;
        }

        private List<RequestedResource> requests() {
            return List.copyOf(requests);
        }
    }

    private record RequestedResource(URI uri, Duration timeout) {}

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }
}
