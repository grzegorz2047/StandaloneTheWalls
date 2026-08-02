package pl.grzegorz2047.standalonethewalls.registry.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal synchronous HTTP boundary used by the bounded registry provider. */
@FunctionalInterface
interface RegistryHttpClient {
    HttpResponse<InputStream> get(URI uri, Duration requestTimeout)
            throws IOException, InterruptedException;
}
