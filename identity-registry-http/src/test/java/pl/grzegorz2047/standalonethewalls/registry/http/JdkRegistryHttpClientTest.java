package pl.grzegorz2047.standalonethewalls.registry.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdkRegistryHttpClientTest {
    @Test
    void buildsGetRequestWithIdentityEncodingAndExplicitTimeout() {
        URI uri = URI.create("https://registry.example/releases/v7/registry-v1.json");
        Duration timeout = Duration.ofSeconds(17);

        HttpRequest request = JdkRegistryHttpClient.buildRequest(uri, timeout);

        assertThat(request.uri()).isEqualTo(uri);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.timeout()).contains(timeout);
        assertThat(request.headers().allValues("Accept-Encoding")).containsExactly("identity");
        assertThat(request.headers().allValues("Authorization")).isEmpty();
        assertThat(request.bodyPublisher()).isEmpty();
    }

    @Test
    void buildsClientWithBoundedConnectTimeoutAndNoHttpsDowngradeRedirects() {
        Duration connectTimeout = Duration.ofSeconds(9);

        HttpClient client = JdkRegistryHttpClient.buildClient(connectTimeout);

        assertThat(client.connectTimeout()).contains(connectTimeout);
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    }
}
