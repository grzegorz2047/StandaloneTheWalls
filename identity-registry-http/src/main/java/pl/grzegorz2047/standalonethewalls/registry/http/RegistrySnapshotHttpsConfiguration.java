package pl.grzegorz2047.standalonethewalls.registry.http;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;

/** Immutable bounded locations and timeouts for one versioned HTTPS snapshot artifact. */
public record RegistrySnapshotHttpsConfiguration(
        URI canonicalJsonUri,
        URI digestUri,
        URI signatureUri,
        Duration connectTimeout,
        Duration requestTimeout,
        int maximumJsonBytes) {
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration MAXIMUM_TIMEOUT = Duration.ofMinutes(2);

    public RegistrySnapshotHttpsConfiguration(
            URI canonicalJsonUri, URI digestUri, URI signatureUri, int maximumJsonBytes) {
        this(
                canonicalJsonUri,
                digestUri,
                signatureUri,
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_REQUEST_TIMEOUT,
                maximumJsonBytes);
    }

    public RegistrySnapshotHttpsConfiguration {
        canonicalJsonUri = requireHttpsUri(canonicalJsonUri, "canonicalJsonUri");
        digestUri = requireHttpsUri(digestUri, "digestUri");
        signatureUri = requireHttpsUri(signatureUri, "signatureUri");
        connectTimeout = requireTimeout(connectTimeout, "connectTimeout");
        requestTimeout = requireTimeout(requestTimeout, "requestTimeout");
        if (maximumJsonBytes < 1
                || maximumJsonBytes > RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES) {
            throw new IllegalArgumentException("maximumJsonBytes is outside the safe range");
        }
        Set<String> resourceKeys = new HashSet<>();
        resourceKeys.add(resourceKey(canonicalJsonUri));
        resourceKeys.add(resourceKey(digestUri));
        resourceKeys.add(resourceKey(signatureUri));
        if (resourceKeys.size() != 3) {
            throw new IllegalArgumentException("registry HTTPS resource URIs must be distinct");
        }
    }

    static URI requireHttpsUri(URI uri, String name) {
        URI value = Objects.requireNonNull(uri, name);
        if (!value.isAbsolute()
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getRawUserInfo() != null
                || value.getRawFragment() != null) {
            throw new IllegalArgumentException(name + " must be an absolute HTTPS URI without userinfo or fragment");
        }
        return value;
    }

    private static Duration requireTimeout(Duration timeout, String name) {
        Duration value = Objects.requireNonNull(timeout, name);
        if (value.isZero() || value.isNegative() || value.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException(name + " is outside the safe range");
        }
        return value;
    }

    private static String resourceKey(URI uri) {
        URI normalized = uri.normalize();
        return normalized.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + normalized.getHost().toLowerCase(Locale.ROOT)
                + (normalized.getPort() == -1 ? "" : ":" + normalized.getPort())
                + Objects.toString(normalized.getRawPath(), "")
                + (normalized.getRawQuery() == null ? "" : "?" + normalized.getRawQuery());
    }
}
