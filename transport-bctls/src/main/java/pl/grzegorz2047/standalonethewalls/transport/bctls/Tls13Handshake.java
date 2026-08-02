package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.jsse.BCSSLSocket;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;

/** Completes one BCJSSE handshake and captures the exporter before BC zeroizes its secret. */
public final class Tls13Handshake {
    private static final Duration MAXIMUM_COMPLETION_TIMEOUT = Duration.ofSeconds(30);

    private Tls13Handshake() {
        throw new AssertionError("No instances");
    }

    public static SecureChannelBinding establish(SSLSocket socket, Duration completionTimeout)
            throws IOException, TlsTransportException {
        Objects.requireNonNull(socket, "socket");
        validateTimeout(completionTimeout);
        if (!(socket instanceof BCSSLSocket bcSocket)) {
            throw new TlsTransportException(
                    TlsTransportException.Code.UNSUPPORTED_JSSE_SOCKET,
                    "the socket is not backed by BCJSSE");
        }

        AtomicReference<SecureChannelBinding> captured = new AtomicReference<>();
        AtomicReference<TlsTransportException> captureFailure = new AtomicReference<>();
        CountDownLatch callbackCompleted = new CountDownLatch(1);
        HandshakeCompletedListener listener =
                event -> {
                    try {
                        captured.compareAndSet(
                                null,
                                TlsChannelBindingExporter.exportDuringHandshakeCallback(bcSocket));
                    } catch (TlsTransportException exception) {
                        captureFailure.compareAndSet(null, exception);
                    } finally {
                        callbackCompleted.countDown();
                    }
                };

        boolean callbackDelivered;
        socket.addHandshakeCompletedListener(listener);
        try {
            socket.startHandshake();
            callbackDelivered =
                    callbackCompleted.await(completionTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            TlsTransportException failure =
                    new TlsTransportException(
                            TlsTransportException.Code.HANDSHAKE_COMPLETION_INTERRUPTED,
                            "interrupted while awaiting the TLS handshake completion callback",
                            exception);
            closeWithSuppressed(socket, failure);
            throw failure;
        } finally {
            socket.removeHandshakeCompletedListener(listener);
        }

        if (!callbackDelivered) {
            TlsTransportException failure =
                    new TlsTransportException(
                            TlsTransportException.Code.HANDSHAKE_COMPLETION_TIMEOUT,
                            "the TLS handshake completion callback exceeded its configured timeout");
            closeWithSuppressed(socket, failure);
            throw failure;
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

    private static void validateTimeout(Duration completionTimeout) {
        Objects.requireNonNull(completionTimeout, "completionTimeout");
        if (completionTimeout.isZero()
                || completionTimeout.isNegative()
                || completionTimeout.compareTo(MAXIMUM_COMPLETION_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "completion timeout must be between 1 nanosecond and 30 seconds");
        }
    }

    private static void closeWithSuppressed(SSLSocket socket, TlsTransportException failure) {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
