package pl.grzegorz2047.standalonethewalls.registry.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** JDK-only synchronous transport; trust remains the responsibility of the snapshot verifier. */
final class JdkRegistryHttpClient implements RegistryHttpClient {
    private final HttpClient client;

    JdkRegistryHttpClient(Duration connectTimeout) {
        client = buildClient(connectTimeout);
    }

    @Override
    public HttpResponse<InputStream> get(URI uri, Duration requestTimeout)
            throws IOException, InterruptedException {
        return client.send(buildRequest(uri, requestTimeout), HttpResponse.BodyHandlers.ofInputStream());
    }

    static HttpClient buildClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static HttpRequest buildRequest(URI uri, Duration requestTimeout) {
        return HttpRequest.newBuilder(Objects.requireNonNull(uri, "uri"))
                .timeout(Objects.requireNonNull(requestTimeout, "requestTimeout"))
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
    }
}
