package pl.grzegorz2047.standalonethewalls.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.UUID;

/** Strict fixed-header codec. It never uses Java native object serialization. */
public final class ProtocolCodec {
    public static final int HEADER_BYTES = 40;
    public static final int MAXIMUM_PAYLOAD_BYTES = 1024 * 1024;
    private static final int MAGIC = 0x53465231; // SFR1
    private static final int SUPPORTED_FLAGS = 0;

    private ProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        byte[] payload = envelope.payload();
        ByteBuffer buffer =
                ByteBuffer.allocate(HEADER_BYTES + payload.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        putUnsignedShort(buffer, envelope.version().major());
        putUnsignedShort(buffer, envelope.version().minor());
        putUnsignedShort(buffer, envelope.messageType().wireId());
        putUnsignedShort(buffer, SUPPORTED_FLAGS);
        putUuid(buffer, envelope.sessionId());
        buffer.putLong(envelope.sequence());
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    public static ProtocolEnvelope decode(byte[] encoded) throws ProtocolException {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length < HEADER_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TRUNCATED_MESSAGE,
                    "message is shorter than the fixed header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != MAGIC) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_MAGIC, "invalid protocol magic");
        }

        ProtocolVersion version =
                new ProtocolVersion(readUnsignedShort(buffer), readUnsignedShort(buffer));
        if (!version.isSupported()) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION,
                    "unsupported protocol version " + version.major() + '.' + version.minor());
        }

        int typeId = readUnsignedShort(buffer);
        MessageType messageType =
                MessageType.fromWireId(typeId)
                        .orElseThrow(
                                () ->
                                        new ProtocolException(
                                                ProtocolException.Code.UNKNOWN_MESSAGE_TYPE,
                                                "unknown message type " + typeId));

        int flags = readUnsignedShort(buffer);
        if (flags != SUPPORTED_FLAGS) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_FLAGS, "unsupported envelope flags");
        }

        UUID sessionId = new UUID(buffer.getLong(), buffer.getLong());
        long sequence = buffer.getLong();
        if (sequence < 0L) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_SEQUENCE, "sequence cannot be negative");
        }

        int payloadLength = buffer.getInt();
        validateLength(messageType, payloadLength);
        if (buffer.remaining() < payloadLength) {
            throw new ProtocolException(
                    ProtocolException.Code.TRUNCATED_MESSAGE,
                    "declared payload is not fully available");
        }
        if (buffer.remaining() > payloadLength) {
            throw new ProtocolException(
                    ProtocolException.Code.TRAILING_BYTES,
                    "message contains bytes after the declared payload");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new ProtocolEnvelope(version, messageType, sessionId, sequence, payload);
    }

    private static void validateLength(MessageType messageType, int payloadLength)
            throws ProtocolException {
        if (payloadLength < 0
                || payloadLength > MAXIMUM_PAYLOAD_BYTES
                || payloadLength > messageType.maximumPayloadBytes()) {
            throw new ProtocolException(
                    ProtocolException.Code.INVALID_LENGTH,
                    "payload length is outside the allowed range");
        }
    }

    private static int readUnsignedShort(ByteBuffer buffer) {
        return Short.toUnsignedInt(buffer.getShort());
    }

    private static void putUnsignedShort(ByteBuffer buffer, int value) {
        buffer.putShort((short) value);
    }

    private static void putUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
    }
}
