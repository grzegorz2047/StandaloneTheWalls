package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

/**
 * Blocking, ordered protocol-envelope stream over one authenticated TLS connection.
 *
 * <p>Callers may use one reader and one writer concurrently. Multiple readers or multiple writers
 * are serialized independently. The class must not be called from the simulation thread.
 */
public final class TlsEnvelopeStream implements AutoCloseable {
    private final Tls13Connection connection;
    private final UUID sessionId;
    private final InputStream input;
    private final OutputStream output;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final StrictEnvelopeSequence inboundSequence = new StrictEnvelopeSequence();
    private final StrictEnvelopeSequence outboundSequence = new StrictEnvelopeSequence();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TlsEnvelopeStream(Tls13Connection connection, UUID sessionId) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.input = connection.inputStream();
        this.output = connection.outputStream();
    }

    public UUID sessionId() {
        return sessionId;
    }

    public Tls13SessionSecurity security() {
        return connection.security();
    }

    public boolean isOpen() {
        return !closed.get();
    }

    public void send(ProtocolEnvelope envelope) throws IOException, ProtocolException {
        Objects.requireNonNull(envelope, "envelope");
        synchronized (writeLock) {
            ensureOpen();
            try {
                validateEnvelope(envelope, outboundSequence);
                output.write(ProtocolCodec.encode(envelope));
                output.flush();
            } catch (IOException | ProtocolException exception) {
                closeAfterFailure(exception);
                throw exception;
            }
        }
    }

    /** Returns an empty result only for a clean EOF before the next fixed header begins. */
    public Optional<ProtocolEnvelope> receive() throws IOException, ProtocolException {
        synchronized (readLock) {
            ensureOpen();
            try {
                int firstByte = input.read();
                if (firstByte < 0) {
                    close();
                    return Optional.empty();
                }

                byte[] header = new byte[ProtocolCodec.HEADER_BYTES];
                header[0] = (byte) firstByte;
                readFully(header, 1, header.length - 1, "protocol header");
                int frameBytes = ProtocolCodec.frameBytesFromHeader(header);
                byte[] encoded = new byte[frameBytes];
                System.arraycopy(header, 0, encoded, 0, header.length);
                readFully(
                        encoded,
                        header.length,
                        frameBytes - header.length,
                        "protocol payload");

                ProtocolEnvelope envelope = ProtocolCodec.decode(encoded);
                validateEnvelope(envelope, inboundSequence);
                return Optional.of(envelope);
            } catch (IOException | ProtocolException exception) {
                closeAfterFailure(exception);
                throw exception;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            connection.close();
        }
    }

    private void validateEnvelope(
            ProtocolEnvelope envelope, StrictEnvelopeSequence sequence)
            throws ProtocolException {
        if (!sessionId.equals(envelope.sessionId())) {
            throw new ProtocolException(
                    ProtocolException.Code.SESSION_MISMATCH,
                    "the envelope belongs to a different transport session");
        }
        ReliableMessagePolicy.requireAllowed(envelope.messageType());
        sequence.accept(envelope.sequence());
    }

    private void readFully(byte[] target, int offset, int length, String part)
            throws IOException, ProtocolException {
        int position = offset;
        int remaining = length;
        while (remaining > 0) {
            int read = input.read(target, position, remaining);
            if (read < 0) {
                throw new ProtocolException(
                        ProtocolException.Code.TRUNCATED_MESSAGE,
                        "the TLS stream ended inside the " + part);
            }
            if (read == 0) {
                int value = input.read();
                if (value < 0) {
                    throw new ProtocolException(
                            ProtocolException.Code.TRUNCATED_MESSAGE,
                            "the TLS stream ended inside the " + part);
                }
                target[position] = (byte) value;
                read = 1;
            }
            position += read;
            remaining -= read;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("the TLS envelope stream is closed");
        }
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
