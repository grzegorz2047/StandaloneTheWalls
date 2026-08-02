package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/** Validates blocking socket limits before low-level TLS reads begin. */
final class Tls13SocketPolicy {
    private static final int MAXIMUM_TIMEOUT_MILLIS = 30_000;

    private Tls13SocketPolicy() {
        throw new AssertionError("No instances");
    }

    static void validate(Socket socket) throws TlsTransportException {
        Objects.requireNonNull(socket, "socket");
        if (!socket.isConnected() || socket.isClosed()) {
            throw invalid("the TLS socket must be connected and open");
        }
        try {
            int timeout = socket.getSoTimeout();
            if (timeout <= 0 || timeout > MAXIMUM_TIMEOUT_MILLIS) {
                throw invalid("the TLS socket read timeout must be between 1 and 30000 ms");
            }
        } catch (SocketException exception) {
            throw new TlsTransportException(
                    TlsTransportException.Code.SOCKET_CONFIGURATION_INVALID,
                    "the TLS socket timeout cannot be inspected",
                    exception);
        }
    }

    private static TlsTransportException invalid(String message) {
        return new TlsTransportException(
                TlsTransportException.Code.SOCKET_CONFIGURATION_INVALID, message);
    }
}
