package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntimeConfiguration;

/** Strict duplicate-detecting loader for a standalone local identity configuration file. */
public final class LocalIdentityProcessConfigurationLoader {
    public static final int MAXIMUM_FILE_BYTES = 64 * 1024;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final String SQLITE_PATH = "identity.sqlite-path";
    private static final String REGISTRY_BUNDLE_PATH = "identity.registry-bundle-path";
    private static final String AUTHORIZATION_MODE = "identity.authorization-mode";
    private static final String TRUST_ROOTS_PATH = "identity.trust-roots-path";
    private static final String REFRESH_SOURCE = "identity.registry.refresh-source";
    private static final String MINIMUM_SEQUENCE = "identity.registry.minimum-sequence";
    private static final String MAXIMUM_AGE_SECONDS = "identity.registry.maximum-age-seconds";
    private static final String MAXIMUM_FUTURE_SKEW_SECONDS =
            "identity.registry.maximum-future-skew-seconds";
    private static final String MAXIMUM_JSON_BYTES = "identity.registry.maximum-json-bytes";
    private static final String MAXIMUM_ENTRIES = "identity.registry.maximum-entries";
    private static final String HTTPS_JSON_URI = "identity.registry.https.json-uri";
    private static final String HTTPS_DIGEST_URI = "identity.registry.https.digest-uri";
    private static final String HTTPS_SIGNATURE_URI = "identity.registry.https.signature-uri";
    private static final String HTTPS_CONNECT_TIMEOUT_SECONDS =
            "identity.registry.https.connect-timeout-seconds";
    private static final String HTTPS_REQUEST_TIMEOUT_SECONDS =
            "identity.registry.https.request-timeout-seconds";
    private static final String SCHEDULER_ENABLED = "identity.registry.scheduler.enabled";
    private static final String SCHEDULER_INITIAL_DELAY_SECONDS =
            "identity.registry.scheduler.initial-delay-seconds";
    private static final String SCHEDULER_SUCCESS_INTERVAL_SECONDS =
            "identity.registry.scheduler.success-interval-seconds";
    private static final String SCHEDULER_INITIAL_FAILURE_BACKOFF_SECONDS =
            "identity.registry.scheduler.initial-failure-backoff-seconds";
    private static final String SCHEDULER_MAXIMUM_FAILURE_BACKOFF_SECONDS =
            "identity.registry.scheduler.maximum-failure-backoff-seconds";
    private static final String SCHEDULER_MAXIMUM_JITTER_SECONDS =
            "identity.registry.scheduler.maximum-jitter-seconds";
    private static final Set<String> REMOTE_REFRESH_KEYS =
            Set.of(
                    HTTPS_JSON_URI,
                    HTTPS_DIGEST_URI,
                    HTTPS_SIGNATURE_URI,
                    HTTPS_CONNECT_TIMEOUT_SECONDS,
                    HTTPS_REQUEST_TIMEOUT_SECONDS,
                    SCHEDULER_ENABLED,
                    SCHEDULER_INITIAL_DELAY_SECONDS,
                    SCHEDULER_SUCCESS_INTERVAL_SECONDS,
                    SCHEDULER_INITIAL_FAILURE_BACKOFF_SECONDS,
                    SCHEDULER_MAXIMUM_FAILURE_BACKOFF_SECONDS,
                    SCHEDULER_MAXIMUM_JITTER_SECONDS);
    private static final Set<String> REQUIRED_HTTPS_KEYS =
            Set.of(HTTPS_JSON_URI, HTTPS_DIGEST_URI, HTTPS_SIGNATURE_URI);
    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    SQLITE_PATH,
                    REGISTRY_BUNDLE_PATH,
                    AUTHORIZATION_MODE,
                    TRUST_ROOTS_PATH,
                    REFRESH_SOURCE,
                    MINIMUM_SEQUENCE,
                    MAXIMUM_AGE_SECONDS,
                    MAXIMUM_FUTURE_SKEW_SECONDS,
                    MAXIMUM_JSON_BYTES,
                    MAXIMUM_ENTRIES,
                    HTTPS_JSON_URI,
                    HTTPS_DIGEST_URI,
                    HTTPS_SIGNATURE_URI,
                    HTTPS_CONNECT_TIMEOUT_SECONDS,
                    HTTPS_REQUEST_TIMEOUT_SECONDS,
                    SCHEDULER_ENABLED,
                    SCHEDULER_INITIAL_DELAY_SECONDS,
                    SCHEDULER_SUCCESS_INTERVAL_SECONDS,
                    SCHEDULER_INITIAL_FAILURE_BACKOFF_SECONDS,
                    SCHEDULER_MAXIMUM_FAILURE_BACKOFF_SECONDS,
                    SCHEDULER_MAXIMUM_JITTER_SECONDS);
    private static final Set<String> REQUIRED_KEYS =
            Set.of(
                    SQLITE_PATH,
                    REGISTRY_BUNDLE_PATH,
                    AUTHORIZATION_MODE,
                    TRUST_ROOTS_PATH,
                    REFRESH_SOURCE);

    private LocalIdentityProcessConfigurationLoader() {
        throw new AssertionError("No instances");
    }

    public static LocalIdentityProcessConfiguration load(Path path)
            throws IOException, RegistrySnapshotException {
        Path configurationPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Path baseDirectory = configurationPath.getParent();
        if (baseDirectory == null || configurationPath.getFileName() == null) {
            throw new IllegalArgumentException("identity configuration path must identify a file");
        }
        Map<String, String> properties = parse(configurationPath);
        for (String requiredKey : REQUIRED_KEYS) {
            if (!properties.containsKey(requiredKey)) {
                throw new IllegalArgumentException(
                        "missing identity configuration key: " + requiredKey);
            }
        }

        Path sqlitePath = resolve(baseDirectory, properties.get(SQLITE_PATH), SQLITE_PATH);
        Path registryBundlePath =
                resolve(baseDirectory, properties.get(REGISTRY_BUNDLE_PATH), REGISTRY_BUNDLE_PATH);
        Path trustRootsPath =
                resolve(baseDirectory, properties.get(TRUST_ROOTS_PATH), TRUST_ROOTS_PATH);
        HandleAuthorizationMode mode = mode(properties.get(AUTHORIZATION_MODE));
        RegistrySnapshotPolicy defaults = RegistrySnapshotPolicy.DEFAULT;
        RegistrySnapshotPolicy policy =
                new RegistrySnapshotPolicy(
                        longValue(properties, MINIMUM_SEQUENCE, defaults.minimumSequence()),
                        Duration.ofSeconds(
                                longValue(
                                        properties,
                                        MAXIMUM_AGE_SECONDS,
                                        defaults.maximumAge().toSeconds())),
                        Duration.ofSeconds(
                                longValue(
                                        properties,
                                        MAXIMUM_FUTURE_SKEW_SECONDS,
                                        defaults.maximumFutureSkew().toSeconds())),
                        intValue(properties, MAXIMUM_JSON_BYTES, defaults.maximumJsonBytes()),
                        intValue(properties, MAXIMUM_ENTRIES, defaults.maximumEntries()));
        RegistryTrustBundle trustBundle = RegistryTrustBundleFileLoader.load(trustRootsPath);
        RegistryRefreshConfiguration refreshConfiguration =
                refreshConfiguration(properties, policy.maximumJsonBytes());

        return new LocalIdentityProcessConfiguration(
                new LocalIdentityRuntimeConfiguration(sqlitePath, registryBundlePath, mode),
                trustRootsPath,
                trustBundle,
                policy,
                refreshConfiguration);
    }

    private static RegistryRefreshConfiguration refreshConfiguration(
            Map<String, String> properties, int maximumJsonBytes) {
        RegistryRefreshConfiguration.Source source;
        try {
            source = RegistryRefreshConfiguration.Source.valueOf(properties.get(REFRESH_SOURCE));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    REFRESH_SOURCE + " must be LOCAL_BUNDLE or HTTPS", exception);
        }
        if (source == RegistryRefreshConfiguration.Source.LOCAL_BUNDLE) {
            for (String key : REMOTE_REFRESH_KEYS) {
                if (properties.containsKey(key)) {
                    throw new IllegalArgumentException(
                            key + " is not allowed for LOCAL_BUNDLE refresh source");
                }
            }
            return new RegistryRefreshConfiguration.LocalBundle();
        }
        for (String requiredKey : REQUIRED_HTTPS_KEYS) {
            if (!properties.containsKey(requiredKey)) {
                throw new IllegalArgumentException(
                        "missing HTTPS registry configuration key: " + requiredKey);
            }
        }
        RegistrySnapshotHttpsConfiguration defaults =
                new RegistrySnapshotHttpsConfiguration(
                        URI.create("https://example.invalid/registry.json"),
                        URI.create("https://example.invalid/registry.sha256"),
                        URI.create("https://example.invalid/registry.sig"),
                        maximumJsonBytes);
        RegistrySnapshotHttpsConfiguration https =
                new RegistrySnapshotHttpsConfiguration(
                        uri(properties.get(HTTPS_JSON_URI), HTTPS_JSON_URI),
                        uri(properties.get(HTTPS_DIGEST_URI), HTTPS_DIGEST_URI),
                        uri(properties.get(HTTPS_SIGNATURE_URI), HTTPS_SIGNATURE_URI),
                        Duration.ofSeconds(
                                longValue(
                                        properties,
                                        HTTPS_CONNECT_TIMEOUT_SECONDS,
                                        defaults.connectTimeout().toSeconds())),
                        Duration.ofSeconds(
                                longValue(
                                        properties,
                                        HTTPS_REQUEST_TIMEOUT_SECONDS,
                                        defaults.requestTimeout().toSeconds())),
                        maximumJsonBytes);
        return new RegistryRefreshConfiguration.Https(https, scheduleConfiguration(properties));
    }

    private static RegistryRefreshScheduleConfiguration scheduleConfiguration(
            Map<String, String> properties) {
        RegistryRefreshScheduleConfiguration defaults =
                RegistryRefreshScheduleConfiguration.DEFAULT;
        return new RegistryRefreshScheduleConfiguration(
                booleanValue(properties, SCHEDULER_ENABLED, defaults.enabled()),
                durationSeconds(
                        properties,
                        SCHEDULER_INITIAL_DELAY_SECONDS,
                        defaults.initialDelay().toSeconds()),
                durationSeconds(
                        properties,
                        SCHEDULER_SUCCESS_INTERVAL_SECONDS,
                        defaults.successInterval().toSeconds()),
                durationSeconds(
                        properties,
                        SCHEDULER_INITIAL_FAILURE_BACKOFF_SECONDS,
                        defaults.initialFailureBackoff().toSeconds()),
                durationSeconds(
                        properties,
                        SCHEDULER_MAXIMUM_FAILURE_BACKOFF_SECONDS,
                        defaults.maximumFailureBackoff().toSeconds()),
                durationSeconds(
                        properties,
                        SCHEDULER_MAXIMUM_JITTER_SECONDS,
                        defaults.maximumJitter().toSeconds()));
    }

    private static Duration durationSeconds(
            Map<String, String> values, String key, long defaultSeconds) {
        long seconds = longValue(values, key, defaultSeconds);
        try {
            Math.multiplyExact(seconds, NANOS_PER_SECOND);
            return Duration.ofSeconds(seconds);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    key + " overflows nanosecond unit conversion", exception);
        }
    }

    private static boolean booleanValue(
            Map<String, String> values, String key, boolean defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    private static URI uri(String value, String key) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " must contain a valid HTTPS URI", exception);
        }
    }

    private static Map<String, String> parse(Path path) throws IOException {
        Map<String, String> properties = new LinkedHashMap<>();
        var lines =
                StrictUtf8TextFile.readLines(
                        path, MAXIMUM_FILE_BYTES, "identity configuration file");
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = index + 1;
            if (!line.equals(line.strip())) {
                throw invalidLine(lineNumber, "must be trimmed");
            }
            if (line.codePoints().anyMatch(Character::isISOControl)) {
                throw invalidLine(lineNumber, "cannot contain control characters");
            }
            int separator = line.indexOf('=');
            if (separator < 1 || separator == line.length() - 1) {
                throw invalidLine(lineNumber, "must use non-empty key=value format");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!key.equals(key.strip()) || !value.equals(value.strip())) {
                throw invalidLine(lineNumber, "key and value must be trimmed");
            }
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown identity configuration key: " + key);
            }
            if (properties.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate identity configuration key: " + key);
            }
        }
        return Map.copyOf(properties);
    }

    private static Path resolve(Path baseDirectory, String value, String key) {
        try {
            Path raw = Path.of(value);
            return (raw.isAbsolute() ? raw : baseDirectory.resolve(raw))
                    .toAbsolutePath()
                    .normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must contain a valid file path", exception);
        }
    }

    private static HandleAuthorizationMode mode(String value) {
        try {
            return HandleAuthorizationMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    AUTHORIZATION_MODE + " must be LOCAL_TOFU, GLOBAL_ONLY, or HYBRID", exception);
        }
    }

    private static long longValue(Map<String, String> values, String key, long defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        requireUnsignedDecimal(value, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    key + " is outside the base-10 integer range", exception);
        }
    }

    private static int intValue(Map<String, String> values, String key, int defaultValue) {
        long value = longValue(values, key, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is outside the base-10 integer range");
        }
        return (int) value;
    }

    private static void requireUnsignedDecimal(String value, String key) {
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(key + " must be an unsigned base-10 integer");
        }
    }

    private static IllegalArgumentException invalidLine(int lineNumber, String reason) {
        return new IllegalArgumentException(
                "identity configuration line " + lineNumber + ' ' + reason);
    }
}
