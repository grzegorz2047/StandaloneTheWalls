package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import org.bouncycastle.tls.TlsProtocol;

/** Owns one authenticated TLS protocol instance and its underlying connected socket. */
public final class Tls13Connection implements AutoCloseable {
    private final Socket socket;
    private final TlsProtocol protocol;
    private final Tls13SessionSecurity security;

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

    @Override
    public void close() throws IOException {
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
