package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.jsse.BCSSLSocket;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;

/** Completes one BCJSSE handshake and captures the exporter before BC zeroizes its secret. */
public final class Tls13Handshake {
    private Tls13Handshake() {
        throw new AssertionError("No instances");
    }

    public static SecureChannelBinding establish(SSLSocket socket)
            throws IOException, TlsTransportException {
        Objects.requireNonNull(socket, "socket");
        if (!(socket instanceof BCSSLSocket bcSocket)) {
            throw new TlsTransportException(
                    TlsTransportException.Code.UNSUPPORTED_JSSE_SOCKET,
                    "the socket is not backed by BCJSSE");
        }

        AtomicReference<SecureChannelBinding> captured = new AtomicReference<>();
        AtomicReference<TlsTransportException> captureFailure = new AtomicReference<>();
        HandshakeCompletedListener listener =
                event -> {
                    try {
                        captured.compareAndSet(
                                null,
                                TlsChannelBindingExporter.exportDuringHandshakeCallback(bcSocket));
                    } catch (TlsTransportException exception) {
                        captureFailure.compareAndSet(null, exception);
                    }
                };

        socket.addHandshakeCompletedListener(listener);
        try {
            socket.startHandshake();
        } finally {
            socket.removeHandshakeCompletedListener(listener);
        }

        TlsTransportException failure = captureFailure.get();
        if (failure != null) {
            closeWithSuppressed(socket, failure);
            throw failure;
        }

        SecureChannelBinding binding = captured.get();
        if (binding == null) {
            TlsTransportException exception =
                    new TlsTransportException(
                            TlsTransportException.Code.CHANNEL_BINDING_UNAVAILABLE,
                            "BCJSSE completed the handshake without delivering tls-exporter");
            closeWithSuppressed(socket, exception);
            throw exception;
        }

        try {
            Tls13Policy.verifyNegotiated(socket);
        } catch (TlsTransportException exception) {
            closeWithSuppressed(socket, exception);
            throw exception;
        }
        return binding;
    }

    private static void closeWithSuppressed(SSLSocket socket, TlsTransportException failure) {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
