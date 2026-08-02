package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Objects;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;

/** Performs one blocking TLS 1.3 server handshake over an accepted socket. */
public final class Tls13ServerAcceptor {
    private Tls13ServerAcceptor() {
        throw new AssertionError("No instances");
    }

    public static Tls13Connection accept(
            Socket socket, Tls13ServerCredentials credentials, SecureRandom secureRandom)
            throws IOException, TlsTransportException {
        Tls13SocketPolicy.validate(socket);
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(secureRandom, "secureRandom");

        JcaTlsCrypto crypto;
        try {
            crypto = BouncyCastleTlsCryptoFactory.create(secureRandom);
        } catch (RuntimeException exception) {
            closeWithSuppressed(socket, exception);
            throw new TlsTransportException(
                    TlsTransportException.Code.CRYPTO_INITIALIZATION_FAILED,
                    "unable to initialize Bouncy Castle TLS cryptography",
                    exception);
        }

        SunderfrontTlsServer server = new SunderfrontTlsServer(crypto, credentials);
        TlsServerProtocol protocol =
                new TlsServerProtocol(socket.getInputStream(), socket.getOutputStream());
        try {
            protocol.accept(server);
            return new Tls13Connection(socket, protocol, server.security());
        } catch (IOException | RuntimeException exception) {
            closeWithSuppressed(socket, exception);
            throw exception;
        }
    }

    private static void closeWithSuppressed(Socket socket, Throwable failure) {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
