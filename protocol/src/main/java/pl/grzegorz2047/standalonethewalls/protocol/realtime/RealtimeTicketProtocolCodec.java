package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Strict big-endian schema-v1 codec for realtime ticket request and result messages. */
public final class RealtimeTicketProtocolCodec {
    public static final int REQUEST_BYTES = 10;
    public static final int ISSUED_RESULT_BYTES = 67;
    public static final int REJECTED_RESULT_BYTES = 12;
    private static final int SCHEMA_VERSION = 1;

    private RealtimeTicketProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeRequest(RealtimeTicketRequest request) {
        RealtimeTicketRequest value = Objects.requireNonNull(request, "request");
        return ByteBuffer.allocate(REQUEST_BYTES)
                .put((byte) SCHEMA_VERSION)
                .put((byte) value.profileVersion())
                .putLong(value.requestId())
                .array();
    }

    public static RealtimeTicketRequest decodeRequest(byte[] payload)
            throws RealtimeTicketProtocolException {
        byte[] bytes = Objects.requireNonNull(payload, "payload");
        requireSize(bytes, REQUEST_BYTES, "realtime ticket request");
        ByteBuffer input = ByteBuffer.wrap(bytes);
        requireSchema(input.get());
        int profileVersion = requireProfile(input.get());
        long requestId = requireRequestId(input.getLong());
        return new RealtimeTicketRequest(requestId, profileVersion);
    }

    public static byte[] encodeIssued(
            long requestId,
            int profileVersion,
            byte[] identity,
            byte[] preSharedKey,
            Instant expiresAt) {
        requireRequestIdForEncode(requestId);
        requireProfileForEncode(profileVersion);
        byte[] identityBytes = Objects.requireNonNull(identity, "identity");
        byte[] keyBytes = Objects.requireNonNull(preSharedKey, "preSharedKey");
        if (identityBytes.length != ClientRealtimeTicket.IDENTITY_BYTES) {
            throw new IllegalArgumentException("realtime identity must contain 16 bytes");
        }
        if (keyBytes.length != ClientRealtimeTicket.PRE_SHARED_KEY_BYTES) {
            throw new IllegalArgumentException("realtime PSK must contain 32 bytes");
        }
        long expirationMillis = Objects.requireNonNull(expiresAt, "expiresAt").toEpochMilli();
        if (expirationMillis < 1L) {
            throw new IllegalArgumentException("expiresAt must be after the Unix epoch");
        }
        return ByteBuffer.allocate(ISSUED_RESULT_BYTES)
                .put((byte) SCHEMA_VERSION)
                .put((byte) RealtimeTicketResultStatus.ISSUED.wireId())
                .put((byte) profileVersion)
                .putLong(requestId)
                .putLong(expirationMillis)
                .put(identityBytes)
                .put(keyBytes)
                .array();
    }

    public static byte[] encodeRejected(
            long requestId, int profileVersion, RealtimeTicketRejection rejection) {
        requireRequestIdForEncode(requestId);
        requireProfileForEncode(profileVersion);
        RealtimeTicketRejection reason = Objects.requireNonNull(rejection, "rejection");
        return ByteBuffer.allocate(REJECTED_RESULT_BYTES)
                .put((byte) SCHEMA_VERSION)
                .put((byte) RealtimeTicketResultStatus.REJECTED.wireId())
                .put((byte) profileVersion)
                .put((byte) reason.wireId())
                .putLong(requestId)
                .array();
    }

    public static RealtimeTicketResult decodeResult(byte[] payload)
            throws RealtimeTicketProtocolException {
        byte[] bytes = Objects.requireNonNull(payload, "payload");
        if (bytes.length < 2) {
            throw invalidSize("realtime ticket result");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes);
        requireSchema(input.get());
        RealtimeTicketResultStatus status =
                RealtimeTicketResultStatus.fromWireId(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new RealtimeTicketProtocolException(
                                                RealtimeTicketProtocolException.Code.INVALID_STATUS,
                                                "realtime ticket result status is invalid"));
        return switch (status) {
            case ISSUED -> decodeIssued(bytes, input);
            case REJECTED -> decodeRejected(bytes, input);
        };
    }

    private static RealtimeTicketResult decodeIssued(byte[] bytes, ByteBuffer input)
            throws RealtimeTicketProtocolException {
        requireSize(bytes, ISSUED_RESULT_BYTES, "issued realtime ticket result");
        int profileVersion = requireProfile(input.get());
        long requestId = requireRequestId(input.getLong());
        long expirationMillis = input.getLong();
        if (expirationMillis < 1L) {
            throw new RealtimeTicketProtocolException(
                    RealtimeTicketProtocolException.Code.INVALID_EXPIRATION,
                    "realtime ticket expiration is invalid");
        }
        byte[] identity = new byte[ClientRealtimeTicket.IDENTITY_BYTES];
        byte[] preSharedKey = new byte[ClientRealtimeTicket.PRE_SHARED_KEY_BYTES];
        input.get(identity);
        input.get(preSharedKey);
        try {
            return RealtimeTicketResult.issued(
                    new ClientRealtimeTicket(
                            requestId,
                            profileVersion,
                            identity,
                            preSharedKey,
                            Instant.ofEpochMilli(expirationMillis)));
        } finally {
            Arrays.fill(preSharedKey, (byte) 0);
        }
    }

    private static RealtimeTicketResult decodeRejected(byte[] bytes, ByteBuffer input)
            throws RealtimeTicketProtocolException {
        requireSize(bytes, REJECTED_RESULT_BYTES, "rejected realtime ticket result");
        int profileVersion = requireProfile(input.get());
        RealtimeTicketRejection rejection =
                RealtimeTicketRejection.fromWireId(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new RealtimeTicketProtocolException(
                                                RealtimeTicketProtocolException.Code
                                                        .INVALID_REJECTION,
                                                "realtime ticket rejection is invalid"));
        long requestId = requireRequestId(input.getLong());
        return RealtimeTicketResult.rejected(requestId, profileVersion, rejection);
    }

    private static void requireSize(byte[] payload, int expected, String description)
            throws RealtimeTicketProtocolException {
        if (payload.length != expected) {
            throw invalidSize(description);
        }
    }

    private static RealtimeTicketProtocolException invalidSize(String description) {
        return new RealtimeTicketProtocolException(
                RealtimeTicketProtocolException.Code.INVALID_SIZE,
                description + " has an invalid size");
    }

    private static void requireSchema(byte raw) throws RealtimeTicketProtocolException {
        if (Byte.toUnsignedInt(raw) != SCHEMA_VERSION) {
            throw new RealtimeTicketProtocolException(
                    RealtimeTicketProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "realtime ticket schema is unsupported");
        }
    }

    private static int requireProfile(byte raw) throws RealtimeTicketProtocolException {
        int profile = Byte.toUnsignedInt(raw);
        if (profile < 1) {
            throw new RealtimeTicketProtocolException(
                    RealtimeTicketProtocolException.Code.INVALID_PROFILE,
                    "realtime profile version is invalid");
        }
        return profile;
    }

    private static long requireRequestId(long requestId) throws RealtimeTicketProtocolException {
        if (requestId < 1L) {
            throw new RealtimeTicketProtocolException(
                    RealtimeTicketProtocolException.Code.INVALID_REQUEST_ID,
                    "realtime ticket requestId is invalid");
        }
        return requestId;
    }

    private static void requireProfileForEncode(int profileVersion) {
        if (profileVersion < 1 || profileVersion > 0xFF) {
            throw new IllegalArgumentException("profileVersion must fit a positive unsigned byte");
        }
    }

    private static void requireRequestIdForEncode(long requestId) {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
    }
}
