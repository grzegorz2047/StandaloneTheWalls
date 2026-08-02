package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/**
 * Blocking, ordered protocol-envelope stream over one authenticated TLS connection.
 *
 * <p>Callers may use one reader and one writer concurrently. Multiple readers or multiple writers
 * are serialized independently. The class must not be called from the simulation thread.
 */
public final class TlsEnvelopeStream implements AutoCloseable, ReliableEnvelopeStream {
    private final UUID sessionId;
    private final InputStream input;
    private final OutputStream output;
    private final Tls13SessionSecurity security;
    private final IoCloseAction closeAction;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final StrictEnvelopeSequence inboundSequence = new StrictEnvelopeSequence();
    private final StrictEnvelopeSequence outboundSequence = new StrictEnvelopeSequence();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TlsEnvelopeStream(Tls13Connection connection, UUID sessionId) {
        this(
                Objects.requireNonNull(connection, "connection").inputStream(),
                connection.outputStream(),
                connection.security(),
                connection::close,
                sessionId);
    }

    TlsEnvelopeStream(AcceptedTlsConnection connection, UUID sessionId) {
        this(
                Objects.requireNonNull(connection, "connection").inputStream(),
                connection.outputStream(),
                connection.security(),
                connection::close,
                sessionId);
    }

    private TlsEnvelopeStream(
            InputStream input,
            OutputStream output,
            Tls13SessionSecurity security,
            IoCloseAction closeAction,
            UUID sessionId) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.security = Objects.requireNonNull(security, "security");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public UUID sessionId() {
        return sessionId;
    }

    public Tls13SessionSecurity security() {
        return security;
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    /** Atomically assigns and returns the next outbound sequence number. */
    @Override
    public long send(MessageType messageType, byte[] payload)
            throws IOException, ProtocolException {
        Objects.requireNonNull(messageType, "messageType");
        byte[] payloadCopy = Objects.requireNonNull(payload, "payload").clone();
        validatePayloadLength(messageType, payloadCopy.length);

        synchronized (writeLock) {
            ensureOpen();
            try {
                ReliableMessagePolicy.requireAllowed(messageType);
                long sequence = outboundSequence.claim();
                ProtocolEnvelope envelope =
                        new ProtocolEnvelope(
                                ProtocolVersion.CURRENT,
                                messageType,
                                sessionId,
                                sequence,
                                payloadCopy);
                output.write(ProtocolCodec.encode(envelope));
                output.flush();
                return sequence;
            } catch (IOException | ProtocolException exception) {
                closeAfterFailure(exception);
                throw exception;
            }
        }
    }

    /** Returns an empty result only for a clean EOF before the next fixed header begins. */
    @Override
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
                readFully(encoded, header.length, frameBytes - header.length, "protocol payload");

                ProtocolEnvelope envelope = ProtocolCodec.decode(encoded);
                validateInboundEnvelope(envelope);
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
            closeAction.close();
        }
    }

    private void validateInboundEnvelope(ProtocolEnvelope envelope) throws ProtocolException {
        if (!sessionId.equals(envelope.sessionId())) {
            throw new ProtocolException(
                    ProtocolException.Code.SESSION_MISMATCH,
                    "the envelope belongs to a different transport session");
        }
        ReliableMessagePolicy.requireAllowed(envelope.messageType());
        inboundSequence.accept(envelope.sequence());
    }

    private void readFully(byte[] target, int offset, int length, String part)
            throws IOException, ProtocolException {
        int position = offset;
        int remaining = length;
        while (remaining > 0) {
            int read = input.read(target, position, remaining);
            if (read < 0) {
                throw truncated(part);
            }
            if (read == 0) {
                int value = input.read();
                if (value < 0) {
                    throw truncated(part);
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

    private static void validatePayloadLength(MessageType messageType, int payloadLength) {
        if (payloadLength > ProtocolCodec.MAXIMUM_PAYLOAD_BYTES
                || payloadLength > messageType.maximumPayloadBytes()) {
            throw new IllegalArgumentException("payload exceeds the allowed message limit");
        }
    }

    private static ProtocolException truncated(String part) {
        return new ProtocolException(
                ProtocolException.Code.TRUNCATED_MESSAGE,
                "the TLS stream ended inside the " + part);
    }

    @FunctionalInterface
    private interface IoCloseAction {
        void close() throws IOException;
    }
}
