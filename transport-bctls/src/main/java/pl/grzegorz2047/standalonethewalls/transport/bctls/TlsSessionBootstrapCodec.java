package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.UUID;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/** Strict fixed-size codec used before the first ordinary protocol envelope. */
public final class TlsSessionBootstrapCodec {
    public static final int RECORD_BYTES = 28;

    private static final int MAGIC = 0x53465342; // SFSB
    private static final int SCHEMA_VERSION = 1;
    private static final int OFFER_TYPE = 1;
    private static final int ACCEPT_TYPE = 2;

    private TlsSessionBootstrapCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeOffer(UUID sessionId) {
        return encode(OFFER_TYPE, sessionId);
    }

    public static byte[] encodeAccept(UUID sessionId) {
        return encode(ACCEPT_TYPE, sessionId);
    }

    public static UUID decodeOffer(byte[] record) throws TlsSessionBootstrapException {
        return decode(record, OFFER_TYPE);
    }

    public static UUID decodeAccept(byte[] record) throws TlsSessionBootstrapException {
        return decode(record, ACCEPT_TYPE);
    }

    static boolean isValidSessionId(UUID sessionId) {
        return sessionId != null
                && (sessionId.getMostSignificantBits() != 0L
                        || sessionId.getLeastSignificantBits() != 0L)
                && sessionId.version() == 4
                && sessionId.variant() == 2;
    }

    private static byte[] encode(int type, UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (!isValidSessionId(sessionId)) {
            throw new IllegalArgumentException("sessionId must be a non-zero RFC 4122 UUIDv4");
        }
        ProtocolVersion version = ProtocolVersion.CURRENT;
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_BYTES).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putShort((short) SCHEMA_VERSION);
        buffer.putShort((short) type);
        buffer.putShort((short) version.major());
        buffer.putShort((short) version.minor());
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID decode(byte[] record, int expectedType)
            throws TlsSessionBootstrapException {
        Objects.requireNonNull(record, "record");
        if (record.length != RECORD_BYTES) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.INVALID_RECORD_SIZE,
                    "session bootstrap record has an invalid size");
        }
        ByteBuffer buffer = ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != MAGIC) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.INVALID_MAGIC,
                    "session bootstrap magic is invalid");
        }
        int schema = Short.toUnsignedInt(buffer.getShort());
        if (schema != SCHEMA_VERSION) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.UNSUPPORTED_SCHEMA,
                    "session bootstrap schema is unsupported");
        }
        int actualType = Short.toUnsignedInt(buffer.getShort());
        if (actualType != expectedType) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.UNEXPECTED_RECORD_TYPE,
                    "session bootstrap record type is unexpected");
        }
        ProtocolVersion protocolVersion =
                new ProtocolVersion(
                        Short.toUnsignedInt(buffer.getShort()),
                        Short.toUnsignedInt(buffer.getShort()));
        if (!protocolVersion.isSupported()) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.UNSUPPORTED_PROTOCOL,
                    "session bootstrap protocol version is unsupported");
        }
        UUID sessionId = new UUID(buffer.getLong(), buffer.getLong());
        if (!isValidSessionId(sessionId)) {
            throw new TlsSessionBootstrapException(
                    TlsSessionBootstrapException.Code.INVALID_SESSION_ID,
                    "session bootstrap UUID is not a non-zero RFC 4122 UUIDv4");
        }
        return sessionId;
    }
}
