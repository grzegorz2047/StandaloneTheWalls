package pl.grzegorz2047.standalonethewalls.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** JDK HTTPS provider with bounded timeouts and redirects disabled. */
public final class JdkHttpsAssetPackProvider implements AssetPackProvider {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkHttpsAssetPackProvider(Duration connectTimeout, Duration requestTimeout) {
        Duration connect = requireTimeout(connectTimeout, "connectTimeout");
        this.requestTimeout = requireTimeout(requestTimeout, "requestTimeout");
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(connect)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public InputStream open(AssetPackReference reference) throws IOException {
        AssetPackReference locked = Objects.requireNonNull(reference, "reference");
        HttpRequest request =
                HttpRequest.newBuilder(locked.url())
                        .timeout(requestTimeout)
                        .header("Accept", "application/zip, application/octet-stream")
                        .GET()
                        .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading the locked asset pack", exception);
        }
        if (response.statusCode() != 200) {
            try (InputStream body = response.body()) {
                // Closing the bounded response body is sufficient; error bodies are never logged.
            }
            throw new IOException("asset provider returned a non-success status");
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength >= 0L && contentLength != locked.size()) {
            try (InputStream body = response.body()) {
                // The caller must never consume a body whose declared size disagrees with the lock.
            }
            throw new IOException("asset provider content length differs from the lock");
        }
        return response.body();
    }

    private static Duration requireTimeout(Duration value, String field) {
        Duration timeout = Objects.requireNonNull(value, field);
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAXIMUM_TIMEOUT) > 0
                || timeout.toMillis() < 1L) {
            throw new IllegalArgumentException(field + " is outside the safe range");
        }
        return timeout;
    }
}
