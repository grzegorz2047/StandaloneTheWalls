package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.tls.TlsProtocol;

/** Owns one authenticated TLS protocol instance and its underlying connected socket. */
public final class Tls13Connection implements AutoCloseable {
    private final Socket socket;
    private final TlsProtocol protocol;
    private final Tls13SessionSecurity security;
    private final AtomicBoolean closed = new AtomicBoolean();

    Tls13Connection(Socket socket, TlsProtocol protocol, Tls13SessionSecurity security) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.security = Objects.requireNonNull(security, "security");
    }

    public InputStream inputStream() {
        return protocol.getInputStream();
    }

    public OutputStream outputStream() {
        return protocol.getOutputStream();
    }

    public Tls13SessionSecurity security() {
        return security;
    }

    void setReadTimeoutMillis(int timeoutMillis) throws SocketException {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("read timeout cannot be negative");
        }
        socket.setSoTimeout(timeoutMillis);
    }

    int readTimeoutMillis() throws SocketException {
        return socket.getSoTimeout();
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = null;
        try {
            protocol.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            socket.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
