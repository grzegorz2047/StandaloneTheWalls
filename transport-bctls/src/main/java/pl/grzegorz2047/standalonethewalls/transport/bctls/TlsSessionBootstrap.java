package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/** Establishes one logical envelope-session UUID inside an authenticated TLS connection. */
public final class TlsSessionBootstrap {
    private TlsSessionBootstrap() {
        throw new AssertionError("No instances");
    }

    public static BootstrappedReliableSession acceptServerSession(
            AcceptedTlsConnection connection,
            TlsSessionBootstrapConfig config,
            SecureRandom random)
            throws IOException, TlsSessionBootstrapException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(random, "random");
        Tls13Connection tlsConnection = connection.tlsConnection();
        try {
            configureBootstrapTimeout(tlsConnection, config);
            UUID sessionId = TlsSessionIds.randomV4(random);
            writeRecord(connection.outputStream(), TlsSessionBootstrapCodec.encodeOffer(sessionId));
            UUID acceptedSession =
                    TlsSessionBootstrapCodec.decodeAccept(readRecord(connection.inputStream()));
            if (!sessionId.equals(acceptedSession)) {
                throw new TlsSessionBootstrapException(
                        TlsSessionBootstrapException.Code.SESSION_MISMATCH,
                        "client accepted a different TLS session UUID");
            }
            tlsConnection.setReadTimeoutMillis(0);
            TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, sessionId);
            return new BootstrappedReliableSession(
                    sessionId,
                    connection.security(),
                    new AsyncTlsReliableChannel(stream));
        } catch (SocketTimeoutException exception) {
            TlsSessionBootstrapException failure = timeout(exception);
            closeWithSuppressed(connection, failure);
            throw failure;
        } catch (IOException | TlsSessionBootstrapException | RuntimeException exception) {
            closeWithSuppressed(connection, exception);
            throw exception;
        }
    }

    public static BootstrappedReliableSession connectClientSession(
            Tls13Connection connection, TlsSessionBootstrapConfig config)
            throws IOException, TlsSessionBootstrapException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(config, "config");
        try {
            configureBootstrapTimeout(connection, config);
            UUID sessionId =
                    TlsSessionBootstrapCodec.decodeOffer(readRecord(connection.inputStream()));
            writeRecord(connection.outputStream(), TlsSessionBootstrapCodec.encodeAccept(sessionId));
            connection.setReadTimeoutMillis(0);
            TlsEnvelopeStream stream = new TlsEnvelopeStream(connection, sessionId);
            return new BootstrappedReliableSession(
                    sessionId,
                    connection.security(),
                    new AsyncTlsReliableChannel(stream));
        } catch (SocketTimeoutException exception) {
            TlsSessionBootstrapException failure = timeout(exception);
            closeWithSuppressed(connection, failure);
            throw failure;
        } catch (IOException | TlsSessionBootstrapException | RuntimeException exception) {
            closeWithSuppressed(connection, exception);
            throw exception;
        }
    }

    private static void configureBootstrapTimeout(
            Tls13Connection connection, TlsSessionBootstrapConfig config) throws IOException {
        connection.setReadTimeoutMillis(config.timeoutMillis());
    }

    private static byte[] readRecord(InputStream input)
            throws IOException, TlsSessionBootstrapException {
        byte[] record = new byte[TlsSessionBootstrapCodec.RECORD_BYTES];
        int offset = 0;
        while (offset < record.length) {
            int read = input.read(record, offset, record.length - offset);
            if (read < 0) {
                throw new TlsSessionBootstrapException(
                        TlsSessionBootstrapException.Code.TRUNCATED_RECORD,
                        "TLS session bootstrap record ended early");
            }
            if (read == 0) {
                int value = input.read();
                if (value < 0) {
                    throw new TlsSessionBootstrapException(
                            TlsSessionBootstrapException.Code.TRUNCATED_RECORD,
                            "TLS session bootstrap record ended early");
                }
                record[offset] = (byte) value;
                offset++;
            } else {
                offset += read;
            }
        }
        return record;
    }

    private static void writeRecord(OutputStream output, byte[] record) throws IOException {
        output.write(record);
        output.flush();
    }

    private static TlsSessionBootstrapException timeout(SocketTimeoutException cause) {
        return new TlsSessionBootstrapException(
                TlsSessionBootstrapException.Code.TIMEOUT,
                "TLS session bootstrap exceeded its configured timeout",
                cause);
    }

    private static void closeWithSuppressed(AutoCloseable connection, Throwable primary) {
        try {
            connection.close();
        } catch (Exception closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }
}
