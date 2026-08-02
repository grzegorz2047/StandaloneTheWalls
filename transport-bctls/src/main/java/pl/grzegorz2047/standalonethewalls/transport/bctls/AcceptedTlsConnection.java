package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One authenticated active-connection lease.
 *
 * <p>Closing the lease closes TLS and releases listener admission exactly once. The raw connection
 * is deliberately package-private so public callers cannot bypass lease accounting.
 */
public final class AcceptedTlsConnection implements AutoCloseable {
    private final long connectionId;
    private final SocketAddress remoteAddress;
    private final Tls13Connection connection;
    private final Runnable releaseAdmission;
    private final AtomicBoolean closed = new AtomicBoolean();

    AcceptedTlsConnection(
            long connectionId,
            SocketAddress remoteAddress,
            Tls13Connection connection,
            Runnable releaseAdmission) {
        if (connectionId < 1L) {
            throw new IllegalArgumentException("connectionId must be positive");
        }
        this.connectionId = connectionId;
        this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.releaseAdmission = Objects.requireNonNull(releaseAdmission, "releaseAdmission");
    }

    public long connectionId() {
        return connectionId;
    }

    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    public InputStream inputStream() {
        return connection.inputStream();
    }

    public OutputStream outputStream() {
        return connection.outputStream();
    }

    public Tls13SessionSecurity security() {
        return connection.security();
    }

    public boolean isOpen() {
        return !closed.get();
    }

    Tls13Connection tlsConnection() {
        return connection;
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            connection.close();
        } finally {
            releaseAdmission.run();
        }
    }

    @Override
    public String toString() {
        return "AcceptedTlsConnection[connectionId="
                + connectionId
                + ", remoteAddress="
                + remoteAddress
                + ", open="
                + isOpen()
                + ']';
    }
}
