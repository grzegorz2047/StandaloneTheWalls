package pl.grzegorz2047.standalonethewalls.server.config.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfiguration;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchangeConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerCredentials;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListenerConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsTransportException;

/** Strict duplicate-detecting loader for one process-owned reliable TLS endpoint. */
public final class ReliableTlsProcessConfigurationLoader {
    public static final int MAXIMUM_CONFIGURATION_BYTES = 64 * 1024;
    public static final int MAXIMUM_PRIVATE_KEY_BYTES = 8 * 1024;
    public static final int MAXIMUM_CERTIFICATE_BYTES = 64 * 1024;

    private static final String SCHEMA = "transport.schema";
    private static final String BIND_ADDRESS = "transport.reliable.bind-address";
    private static final String PRIVATE_KEY_PATH = "transport.reliable.private-key-pkcs8-path";
    private static final String CERTIFICATE_PATH = "transport.reliable.certificate-x509-path";
    private static final String BACKLOG = "transport.reliable.backlog";
    private static final String MAXIMUM_CONCURRENT_HANDSHAKES =
            "transport.reliable.maximum-concurrent-handshakes";
    private static final String MAXIMUM_ACTIVE_CONNECTIONS =
            "transport.reliable.maximum-active-connections";
    private static final String HANDSHAKE_TIMEOUT_SECONDS =
            "transport.reliable.handshake-timeout-seconds";
    private static final String LISTENER_SHUTDOWN_TIMEOUT_SECONDS =
            "transport.reliable.shutdown-timeout-seconds";
    private static final String CHALLENGE_LIFETIME_SECONDS =
            "transport.identity.challenge-lifetime-seconds";
    private static final String MAXIMUM_OUTSTANDING_CHALLENGES =
            "transport.identity.maximum-outstanding-challenges";
    private static final String RESULT_SEND_TIMEOUT_SECONDS =
            "transport.identity.result-send-timeout-seconds";
    private static final String GATEWAY_SHUTDOWN_TIMEOUT_SECONDS =
            "transport.identity.gateway-shutdown-timeout-seconds";

    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    SCHEMA,
                    BIND_ADDRESS,
                    PRIVATE_KEY_PATH,
                    CERTIFICATE_PATH,
                    BACKLOG,
                    MAXIMUM_CONCURRENT_HANDSHAKES,
                    MAXIMUM_ACTIVE_CONNECTIONS,
                    HANDSHAKE_TIMEOUT_SECONDS,
                    LISTENER_SHUTDOWN_TIMEOUT_SECONDS,
                    CHALLENGE_LIFETIME_SECONDS,
                    MAXIMUM_OUTSTANDING_CHALLENGES,
                    RESULT_SEND_TIMEOUT_SECONDS,
                    GATEWAY_SHUTDOWN_TIMEOUT_SECONDS);
    private static final Set<String> REQUIRED_KEYS =
            Set.of(SCHEMA, PRIVATE_KEY_PATH, CERTIFICATE_PATH);
    private static final byte[] CREDENTIAL_MATCH_PROBE =
            "SUNDERFRONT-SERVER-CREDENTIAL-MATCH-V1"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private ReliableTlsProcessConfigurationLoader() {
        throw new AssertionError("No instances");
    }

    public static ReliableTlsProcessConfiguration load(
            Path path, ServerConfiguration serverConfiguration) throws IOException {
        Path configurationPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        ServerConfiguration server =
                Objects.requireNonNull(serverConfiguration, "serverConfiguration");
        Path baseDirectory = configurationPath.getParent();
        if (baseDirectory == null || configurationPath.getFileName() == null) {
            throw new IllegalArgumentException("TLS configuration path must identify a file");
        }

        Map<String, String> properties = parse(configurationPath);
        for (String requiredKey : REQUIRED_KEYS) {
            if (!properties.containsKey(requiredKey)) {
                throw new IllegalArgumentException("missing TLS configuration key: " + requiredKey);
            }
        }
        if (!"1".equals(properties.get(SCHEMA))) {
            throw new IllegalArgumentException("transport.schema must be exactly 1");
        }

        Path privateKeyPath =
                resolve(baseDirectory, properties.get(PRIVATE_KEY_PATH), PRIVATE_KEY_PATH);
        Path certificatePath =
                resolve(baseDirectory, properties.get(CERTIFICATE_PATH), CERTIFICATE_PATH);
        if (privateKeyPath.equals(certificatePath)) {
            throw new IllegalArgumentException(
                    "TLS private-key and certificate paths must identify different files");
        }

        PrivateKey privateKey = loadPrivateKey(privateKeyPath);
        X509Certificate certificate = loadCertificate(certificatePath);
        verifyKeyPair(privateKey, certificate);
        Tls13ServerCredentials credentials = createCredentials(privateKey, certificate);

        int maximumActiveConnections =
                integer(properties, MAXIMUM_ACTIVE_CONNECTIONS, server.maximumPlayers());
        if (maximumActiveConnections > server.maximumPlayers()) {
            throw new IllegalArgumentException(
                    MAXIMUM_ACTIVE_CONNECTIONS + " cannot exceed server.maximum-players");
        }
        int maximumConcurrentHandshakes =
                integer(
                        properties,
                        MAXIMUM_CONCURRENT_HANDSHAKES,
                        Math.min(16, maximumActiveConnections));
        int maximumOutstandingChallenges =
                integer(properties, MAXIMUM_OUTSTANDING_CHALLENGES, maximumActiveConnections);
        if (maximumOutstandingChallenges > maximumActiveConnections) {
            throw new IllegalArgumentException(
                    MAXIMUM_OUTSTANDING_CHALLENGES + " cannot exceed maximum active connections");
        }

        Duration challengeLifetime = seconds(properties, CHALLENGE_LIFETIME_SECONDS, 30L);
        if (challengeLifetime.compareTo(IdentityExchangeConfig.DEFAULT.overallTimeout()) < 0) {
            throw new IllegalArgumentException(
                    CHALLENGE_LIFETIME_SECONDS
                            + " cannot be shorter than the identity exchange overall timeout");
        }

        InetAddress bindAddress = numericAddress(properties.getOrDefault(BIND_ADDRESS, "0.0.0.0"));
        Tls13ServerListenerConfig listenerConfig =
                new Tls13ServerListenerConfig(
                        new InetSocketAddress(bindAddress, server.reliablePort()),
                        integer(properties, BACKLOG, 128),
                        maximumConcurrentHandshakes,
                        maximumActiveConnections,
                        seconds(properties, HANDSHAKE_TIMEOUT_SECONDS, 10L),
                        seconds(properties, LISTENER_SHUTDOWN_TIMEOUT_SECONDS, 5L));

        return new ReliableTlsProcessConfiguration(
                listenerConfig,
                credentials,
                challengeLifetime,
                maximumOutstandingChallenges,
                seconds(properties, RESULT_SEND_TIMEOUT_SECONDS, 5L),
                seconds(properties, GATEWAY_SHUTDOWN_TIMEOUT_SECONDS, 5L));
    }

    private static Tls13ServerCredentials createCredentials(
            PrivateKey privateKey, X509Certificate certificate) {
        try {
            return Tls13ServerCredentials.create(privateKey, List.of(certificate));
        } catch (TlsTransportException exception) {
            throw new IllegalArgumentException("TLS server credentials are invalid", exception);
        }
    }

    private static PrivateKey loadPrivateKey(Path path) throws IOException {
        byte[] encoded =
                readBoundedRegularFile(path, MAXIMUM_PRIVATE_KEY_BYTES, "TLS private-key file");
        try {
            PrivateKey privateKey =
                    KeyFactory.getInstance("Ed25519")
                            .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            byte[] canonical = privateKey.getEncoded();
            if (canonical == null || !MessageDigest.isEqual(encoded, canonical)) {
                throw new IllegalArgumentException(
                        "TLS private-key file must contain canonical Ed25519 PKCS#8 DER");
            }
            return privateKey;
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(
                    "TLS private-key file must contain Ed25519 PKCS#8 DER", exception);
        }
    }

    private static X509Certificate loadCertificate(Path path) throws IOException {
        byte[] encoded =
                readBoundedRegularFile(path, MAXIMUM_CERTIFICATE_BYTES, "TLS certificate file");
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream input = new ByteArrayInputStream(encoded);
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(input);
            if (input.available() != 0
                    || !MessageDigest.isEqual(encoded, certificate.getEncoded())) {
                throw new IllegalArgumentException(
                        "TLS certificate file must contain one canonical X.509 DER certificate");
            }
            return certificate;
        } catch (CertificateException exception) {
            throw new IllegalArgumentException(
                    "TLS certificate file must contain one X.509 DER certificate", exception);
        }
    }

    private static void verifyKeyPair(PrivateKey privateKey, X509Certificate certificate) {
        if (!"Ed25519".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
            throw new IllegalArgumentException("TLS certificate leaf public key must use Ed25519");
        }
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(CREDENTIAL_MATCH_PROBE);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(CREDENTIAL_MATCH_PROBE);
            if (!verifier.verify(signature)) {
                throw new IllegalArgumentException(
                        "TLS private key does not match the certificate public key");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("TLS credential pair cannot be verified", exception);
        }
    }

    private static byte[] readBoundedRegularFile(Path path, int maximumBytes, String label)
            throws IOException {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be a regular non-symbolic-link file");
        }
        byte[] encoded;
        try (InputStream input = Files.newInputStream(normalized)) {
            encoded = input.readNBytes(maximumBytes + 1);
        }
        if (encoded.length == 0 || encoded.length > maximumBytes) {
            throw new IllegalArgumentException(
                    label + " is empty or exceeds its maximum byte size");
        }
        return encoded;
    }

    private static Map<String, String> parse(Path path) throws IOException {
        byte[] encoded =
                readBoundedRegularFile(path, MAXIMUM_CONFIGURATION_BYTES, "TLS configuration file");
        String text;
        try {
            text =
                    java.nio.charset.StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(encoded))
                            .toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "TLS configuration file must contain valid UTF-8", exception);
        }

        Map<String, String> properties = new LinkedHashMap<>();
        String[] lines = text.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            } else if (line.indexOf('\r') >= 0) {
                throw invalidLine(index + 1, "contains an invalid line ending");
            }
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
                throw new IllegalArgumentException("unknown TLS configuration key: " + key);
            }
            if (properties.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate TLS configuration key: " + key);
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

    private static InetAddress numericAddress(String value) {
        String address = Objects.requireNonNull(value, BIND_ADDRESS);
        if (!address.equals(address.strip()) || address.isEmpty()) {
            throw new IllegalArgumentException(BIND_ADDRESS + " must be a trimmed numeric address");
        }
        if (address.indexOf(':') >= 0) {
            if (!address.chars()
                    .allMatch(
                            character ->
                                    character == ':'
                                            || character == '.'
                                            || Character.digit(character, 16) >= 0)) {
                throw new IllegalArgumentException(
                        BIND_ADDRESS + " must be a numeric IPv4 or IPv6 address");
            }
            try {
                return InetAddress.getByName(address);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException(
                        BIND_ADDRESS + " is not a valid IPv6 address", exception);
            }
        }

        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    BIND_ADDRESS + " must be a numeric IPv4 or IPv6 address");
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()
                    || (part.length() > 1 && part.charAt(0) == '0')
                    || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException(BIND_ADDRESS + " is not canonical IPv4");
            }
            int component;
            try {
                component = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(BIND_ADDRESS + " is not valid IPv4", exception);
            }
            if (component > 255) {
                throw new IllegalArgumentException(BIND_ADDRESS + " is not valid IPv4");
            }
            bytes[index] = (byte) component;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(BIND_ADDRESS + " is not valid IPv4", exception);
        }
    }

    private static int integer(Map<String, String> values, String key, int defaultValue) {
        long value = unsignedLong(values, key, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is outside the base-10 integer range");
        }
        return (int) value;
    }

    private static Duration seconds(Map<String, String> values, String key, long defaultValue) {
        long value = unsignedLong(values, key, defaultValue);
        try {
            return Duration.ofSeconds(value);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(key + " is outside the duration range", exception);
        }
    }

    private static long unsignedLong(Map<String, String> values, String key, long defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(key + " must be an unsigned base-10 integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    key + " is outside the base-10 integer range", exception);
        }
    }

    private static IllegalArgumentException invalidLine(int lineNumber, String reason) {
        return new IllegalArgumentException("TLS configuration line " + lineNumber + ' ' + reason);
    }
}
