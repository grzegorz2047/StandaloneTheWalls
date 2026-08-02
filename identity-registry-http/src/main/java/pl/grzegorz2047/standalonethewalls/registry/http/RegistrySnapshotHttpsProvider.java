package pl.grzegorz2047.standalonethewalls.registry.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;

/**
 * Fetches one detached signed artifact from explicit HTTPS resources. Returned bytes remain
 * untrusted until the core registry verifier accepts them.
 */
public final class RegistrySnapshotHttpsProvider implements RegistrySnapshotProvider {
    private static final String FAILURE_MESSAGE =
            "HTTPS registry snapshot resources are unavailable or invalid";
    private static final int DIGEST_HEX_CHARACTERS = RegistrySnapshotArtifact.DIGEST_BYTES * 2;
    private static final int SIGNATURE_HEX_CHARACTERS =
            RegistrySnapshotArtifact.SIGNATURE_BYTES * 2;

    private final RegistrySnapshotHttpsConfiguration configuration;
    private final RegistryHttpClient client;

    public RegistrySnapshotHttpsProvider(RegistrySnapshotHttpsConfiguration configuration) {
        this(
                configuration,
                new JdkRegistryHttpClient(
                        Objects.requireNonNull(configuration, "configuration").connectTimeout()));
    }

    RegistrySnapshotHttpsProvider(
            RegistrySnapshotHttpsConfiguration configuration, RegistryHttpClient client) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public RegistrySnapshotArtifact load() throws RegistrySnapshotProviderException {
        try {
            byte[] canonicalJson =
                    loadResource(
                            configuration.canonicalJsonUri(), configuration.maximumJsonBytes());
            byte[] digestLine = loadResource(configuration.digestUri(), DIGEST_HEX_CHARACTERS + 1);
            byte[] signatureLine =
                    loadResource(configuration.signatureUri(), SIGNATURE_HEX_CHARACTERS + 1);
            return new RegistrySnapshotArtifact(
                    canonicalJson,
                    decodeLowercaseHexLine(
                            digestLine,
                            DIGEST_HEX_CHARACTERS,
                            RegistrySnapshotArtifact.DIGEST_BYTES),
                    decodeLowercaseHexLine(
                            signatureLine,
                            SIGNATURE_HEX_CHARACTERS,
                            RegistrySnapshotArtifact.SIGNATURE_BYTES));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistrySnapshotProviderException(FAILURE_MESSAGE, exception);
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            throw new RegistrySnapshotProviderException(FAILURE_MESSAGE, exception);
        }
    }

    private byte[] loadResource(java.net.URI uri, int maximumBytes)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.get(uri, configuration.requestTimeout());
        try (InputStream body = Objects.requireNonNull(response.body(), "response body")) {
            RegistrySnapshotHttpsConfiguration.requireHttpsUri(response.uri(), "response URI");
            if (response.statusCode() != 200) {
                throw new IOException("registry HTTPS response status is not accepted");
            }
            requireIdentityContentEncoding(response.headers());
            long declaredLength = declaredContentLength(response.headers(), maximumBytes);
            byte[] bytes = body.readNBytes(maximumBytes + 1);
            if (bytes.length == 0 || bytes.length > maximumBytes) {
                throw new IOException(
                        "registry HTTPS response body size is outside the safe range");
            }
            if (declaredLength >= 0L && declaredLength != bytes.length) {
                throw new IOException("registry HTTPS response length does not match its header");
            }
            return bytes;
        }
    }

    private static void requireIdentityContentEncoding(HttpHeaders headers) throws IOException {
        List<String> values = headers.allValues("Content-Encoding");
        for (String value : values) {
            String[] codings = value.split(",", -1);
            for (String coding : codings) {
                if (!"identity".equals(coding.trim().toLowerCase(Locale.ROOT))) {
                    throw new IOException(
                            "registry HTTPS response content encoding is not accepted");
                }
            }
        }
    }

    private static long declaredContentLength(HttpHeaders headers, int maximumBytes)
            throws IOException {
        List<String> values = headers.allValues("Content-Length");
        if (values.isEmpty()) {
            return -1L;
        }
        if (values.size() != 1) {
            throw new IOException("registry HTTPS response contains ambiguous content length");
        }
        String value = values.getFirst();
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw new IOException("registry HTTPS response content length is malformed");
        }
        try {
            long length = Long.parseLong(value);
            if (length < 1L || length > maximumBytes) {
                throw new IOException(
                        "registry HTTPS response content length is outside the safe range");
            }
            return length;
        } catch (NumberFormatException exception) {
            throw new IOException("registry HTTPS response content length is malformed", exception);
        }
    }

    private static byte[] decodeLowercaseHexLine(
            byte[] encoded, int expectedCharacters, int decodedBytes) {
        if (encoded.length != expectedCharacters + 1 || encoded[expectedCharacters] != '\n') {
            throw new IllegalArgumentException(
                    "registry detached value must use exact lowercase hexadecimal line format");
        }
        byte[] decoded = new byte[decodedBytes];
        for (int index = 0; index < expectedCharacters; index += 2) {
            int high = lowercaseHexNibble(encoded[index]);
            int low = lowercaseHexNibble(encoded[index + 1]);
            decoded[index / 2] = (byte) ((high << 4) | low);
        }
        return decoded;
    }

    private static int lowercaseHexNibble(byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        throw new IllegalArgumentException(
                "registry detached value must use lowercase hexadecimal");
    }
}
