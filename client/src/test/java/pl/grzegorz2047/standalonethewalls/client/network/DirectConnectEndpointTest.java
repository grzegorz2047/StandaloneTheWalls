package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DirectConnectEndpointTest {
    @ParameterizedTest
    @MethodSource("validEndpoints")
    void parsesAndCanonicalizesSupportedEndpoints(
            String input,
            String expectedHost,
            int expectedPort,
            DirectConnectEndpoint.HostKind expectedKind,
            String expectedAuthority)
            throws DirectConnectEndpointException {
        DirectConnectEndpoint endpoint = DirectConnectEndpoint.parse(input);

        assertEquals(expectedHost, endpoint.host());
        assertEquals(expectedPort, endpoint.port());
        assertEquals(expectedKind, endpoint.hostKind());
        assertEquals(expectedAuthority, endpoint.authority());
        assertEquals(expectedAuthority, endpoint.toString());
        assertEquals(expectedAuthority, endpoint.serverReference().value());
        assertEquals(expectedHost, endpoint.unresolvedSocketAddress().getHostString());
        assertEquals(expectedPort, endpoint.unresolvedSocketAddress().getPort());
        assertFalse(endpoint.unresolvedSocketAddress().isUnresolved() && expectedKind != DirectConnectEndpoint.HostKind.DNS);
    }

    @ParameterizedTest
    @MethodSource("invalidEndpoints")
    void rejectsInvalidOrAmbiguousEndpoints(
            String input, DirectConnectEndpointException.Code expectedCode) {
        DirectConnectEndpointException exception =
                assertThrows(
                        DirectConnectEndpointException.class,
                        () -> DirectConnectEndpoint.parse(input));

        assertEquals(expectedCode, exception.code());
    }

    @Test
    void valueEqualityUsesCanonicalHostPortAndKind() throws DirectConnectEndpointException {
        DirectConnectEndpoint first = DirectConnectEndpoint.parse("Example.COM:27420");
        DirectConnectEndpoint second = DirectConnectEndpoint.parse("example.com:27420");
        DirectConnectEndpoint different = DirectConnectEndpoint.parse("example.com:27421");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        org.junit.jupiter.api.Assertions.assertNotEquals(first, different);
    }

    private static Stream<Arguments> validEndpoints() {
        return Stream.of(
                Arguments.of(
                        "127.0.0.1:27420",
                        "127.0.0.1",
                        27420,
                        DirectConnectEndpoint.HostKind.IPV4,
                        "127.0.0.1:27420"),
                Arguments.of(
                        "LOCALHOST:1",
                        "localhost",
                        1,
                        DirectConnectEndpoint.HostKind.DNS,
                        "localhost:1"),
                Arguments.of(
                        "Server-01.Example.COM:65535",
                        "server-01.example.com",
                        65535,
                        DirectConnectEndpoint.HostKind.DNS,
                        "server-01.example.com:65535"),
                Arguments.of(
                        "[::1]:27420",
                        "::1",
                        27420,
                        DirectConnectEndpoint.HostKind.IPV6,
                        "[::1]:27420"),
                Arguments.of(
                        "[2001:0DB8:0:0:0:0:0:1]:443",
                        "2001:db8::1",
                        443,
                        DirectConnectEndpoint.HostKind.IPV6,
                        "[2001:db8::1]:443"));
    }

    private static Stream<Arguments> invalidEndpoints() {
        return Stream.of(
                Arguments.of("", DirectConnectEndpointException.Code.EMPTY),
                Arguments.of(" localhost:27420", DirectConnectEndpointException.Code.WHITESPACE),
                Arguments.of("localhost:27420 ", DirectConnectEndpointException.Code.WHITESPACE),
                Arguments.of("https://localhost:27420", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("user@localhost:27420", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("localhost:27420/path", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("localhost:27420?x=1", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("localhost:27420#fragment", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("localhost", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("localhost:", DirectConnectEndpointException.Code.INVALID_PORT),
                Arguments.of("localhost:027420", DirectConnectEndpointException.Code.INVALID_PORT),
                Arguments.of("localhost:+27420", DirectConnectEndpointException.Code.INVALID_PORT),
                Arguments.of("localhost:0", DirectConnectEndpointException.Code.PORT_OUT_OF_RANGE),
                Arguments.of("localhost:65536", DirectConnectEndpointException.Code.PORT_OUT_OF_RANGE),
                Arguments.of("::1:27420", DirectConnectEndpointException.Code.IPV6_REQUIRES_BRACKETS),
                Arguments.of("[::1]27420", DirectConnectEndpointException.Code.INVALID_SYNTAX),
                Arguments.of("[::1]:", DirectConnectEndpointException.Code.INVALID_PORT),
                Arguments.of("[fe80::1%eth0]:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("127.0.0.01:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("256.0.0.1:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("example.com.:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("-example.com:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("example_.com:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of("żółw.example:27420", DirectConnectEndpointException.Code.INVALID_HOST),
                Arguments.of(
                        "a".repeat(301), DirectConnectEndpointException.Code.TOO_LONG));
    }
}
