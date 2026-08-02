package pl.grzegorz2047.standalonethewalls.client.network;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;

/** Canonical user-supplied TCP endpoint without DNS resolution or an implicit port. */
public final class DirectConnectEndpoint {
    public static final int MAXIMUM_INPUT_CHARACTERS = 300;

    private final String host;
    private final int port;
    private final HostKind hostKind;
    private final String authority;

    private DirectConnectEndpoint(String host, int port, HostKind hostKind) {
        this.host = host;
        this.port = port;
        this.hostKind = hostKind;
        authority = hostKind == HostKind.IPV6 ? '[' + host + "]:" + port : host + ':' + port;
    }

    public static DirectConnectEndpoint parse(String raw) throws DirectConnectEndpointException {
        Objects.requireNonNull(raw, "raw");
        if (raw.isEmpty()) {
            throw failure(DirectConnectEndpointException.Code.EMPTY, "endpoint is empty");
        }
        if (raw.length() > MAXIMUM_INPUT_CHARACTERS) {
            throw failure(
                    DirectConnectEndpointException.Code.TOO_LONG,
                    "endpoint exceeds the accepted length");
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw failure(
                    DirectConnectEndpointException.Code.WHITESPACE,
                    "endpoint cannot contain whitespace");
        }
        if (raw.contains("://")
                || raw.indexOf('/') >= 0
                || raw.indexOf('?') >= 0
                || raw.indexOf('#') >= 0
                || raw.indexOf('@') >= 0) {
            throw failure(
                    DirectConnectEndpointException.Code.INVALID_SYNTAX,
                    "endpoint must contain only a host and explicit port");
        }

        String hostValue;
        String portValue;
        HostKind kind;
        if (raw.charAt(0) == '[') {
            int closingBracket = raw.indexOf(']');
            if (closingBracket < 2
                    || closingBracket != raw.lastIndexOf(']')
                    || closingBracket + 2 > raw.length()
                    || raw.charAt(closingBracket + 1) != ':') {
                throw failure(
                        DirectConnectEndpointException.Code.INVALID_SYNTAX,
                        "bracketed IPv6 endpoint is malformed");
            }
            hostValue = canonicalIpv6(raw.substring(1, closingBracket));
            portValue = raw.substring(closingBracket + 2);
            kind = HostKind.IPV6;
        } else {
            int firstColon = raw.indexOf(':');
            if (firstColon < 1 || firstColon != raw.lastIndexOf(':')) {
                throw failure(
                        firstColon != raw.lastIndexOf(':')
                                ? DirectConnectEndpointException.Code.IPV6_REQUIRES_BRACKETS
                                : DirectConnectEndpointException.Code.INVALID_SYNTAX,
                        firstColon != raw.lastIndexOf(':')
                                ? "IPv6 endpoints require brackets"
                                : "endpoint requires a host and explicit port");
            }
            hostValue = raw.substring(0, firstColon);
            portValue = raw.substring(firstColon + 1);
            if (looksLikeIpv4(hostValue)) {
                hostValue = canonicalIpv4(hostValue);
                kind = HostKind.IPV4;
            } else {
                hostValue = canonicalDnsName(hostValue);
                kind = HostKind.DNS;
            }
        }

        int parsedPort = parsePort(portValue);
        return new DirectConnectEndpoint(hostValue, parsedPort, kind);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public HostKind hostKind() {
        return hostKind;
    }

    public String authority() {
        return authority;
    }

    public ServerReference serverReference() {
        return new ServerReference(authority);
    }

    public InetSocketAddress unresolvedSocketAddress() {
        return InetSocketAddress.createUnresolved(host, port);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DirectConnectEndpoint endpoint
                        && port == endpoint.port
                        && host.equals(endpoint.host)
                        && hostKind == endpoint.hostKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, hostKind);
    }

    @Override
    public String toString() {
        return authority;
    }

    private static int parsePort(String value) throws DirectConnectEndpointException {
        if (value.isEmpty()
                || value.length() > 5
                || value.length() > 1 && value.charAt(0) == '0'
                || !value.chars().allMatch(Character::isDigit)) {
            throw failure(
                    DirectConnectEndpointException.Code.INVALID_PORT,
                    "port must be a canonical base-10 integer");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw failure(
                    DirectConnectEndpointException.Code.INVALID_PORT,
                    "port must be a canonical base-10 integer");
        }
        if (parsed < 1 || parsed > 65_535) {
            throw failure(
                    DirectConnectEndpointException.Code.PORT_OUT_OF_RANGE,
                    "port must be between 1 and 65535");
        }
        return parsed;
    }

    private static boolean looksLikeIpv4(String value) {
        return !value.isEmpty() && value.chars().allMatch(character -> character == '.' || Character.isDigit(character));
    }

    private static String canonicalIpv4(String value) throws DirectConnectEndpointException {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            throw invalidHost();
        }
        StringBuilder canonical = new StringBuilder(value.length());
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty()
                    || octet.length() > 3
                    || octet.length() > 1 && octet.charAt(0) == '0') {
                throw invalidHost();
            }
            int parsed;
            try {
                parsed = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                throw invalidHost();
            }
            if (parsed > 255) {
                throw invalidHost();
            }
            if (index > 0) {
                canonical.append('.');
            }
            canonical.append(parsed);
        }
        return canonical.toString();
    }

    private static String canonicalDnsName(String value) throws DirectConnectEndpointException {
        if (value.isEmpty()
                || value.length() > 253
                || value.endsWith(".")
                || !value.chars().allMatch(character -> character < 128)) {
            throw invalidHost();
        }
        String canonical = value.toLowerCase(Locale.ROOT);
        String[] labels = canonical.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty()
                    || label.length() > 63
                    || !isAsciiLetterOrDigit(label.charAt(0))
                    || !isAsciiLetterOrDigit(label.charAt(label.length() - 1))) {
                throw invalidHost();
            }
            for (int index = 1; index < label.length() - 1; index++) {
                char character = label.charAt(index);
                if (!isAsciiLetterOrDigit(character) && character != '-') {
                    throw invalidHost();
                }
            }
        }
        return canonical;
    }

    private static String canonicalIpv6(String value) throws DirectConnectEndpointException {
        if (value.isEmpty()
                || value.indexOf('%') >= 0
                || !value.chars()
                        .allMatch(
                                character ->
                                        character == ':'
                                                || character == '.'
                                                || character >= '0' && character <= '9'
                                                || character >= 'a' && character <= 'f'
                                                || character >= 'A' && character <= 'F')) {
            throw invalidHost();
        }
        InetAddress parsed;
        try {
            parsed = InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            throw invalidHost();
        }
        if (!(parsed instanceof Inet6Address)) {
            throw invalidHost();
        }
        byte[] bytes = parsed.getAddress();
        int[] words = new int[8];
        for (int index = 0; index < words.length; index++) {
            words[index] =
                    Byte.toUnsignedInt(bytes[index * 2]) << 8
                            | Byte.toUnsignedInt(bytes[index * 2 + 1]);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int index = 0; index < words.length; ) {
            if (words[index] != 0) {
                index++;
                continue;
            }
            int start = index;
            while (index < words.length && words[index] == 0) {
                index++;
            }
            int length = index - start;
            if (length >= 2 && length > bestLength) {
                bestStart = start;
                bestLength = length;
            }
        }

        StringBuilder canonical = new StringBuilder(39);
        for (int index = 0; index < words.length; index++) {
            if (index == bestStart) {
                canonical.append("::");
                index += bestLength - 1;
                continue;
            }
            if (canonical.length() > 0 && canonical.charAt(canonical.length() - 1) != ':') {
                canonical.append(':');
            }
            canonical.append(Integer.toHexString(words[index]));
        }
        return canonical.toString();
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9';
    }

    private static DirectConnectEndpointException invalidHost() {
        return failure(
                DirectConnectEndpointException.Code.INVALID_HOST,
                "host is not a canonical IPv4, IPv6, or ASCII DNS name");
    }

    private static DirectConnectEndpointException failure(
            DirectConnectEndpointException.Code code, String message) {
        return new DirectConnectEndpointException(code, message);
    }

    public enum HostKind {
        DNS,
        IPV4,
        IPV6
    }
}
