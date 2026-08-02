package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;

/** Fixed-size versioned codec for one post-authentication player admission result. */
public final class PlayerSessionAdmissionCodec {
    public static final int PAYLOAD_BYTES = 2;
    private static final int SCHEMA_VERSION = 1;

    private PlayerSessionAdmissionCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(PlayerSessionAdmissionStatus status) {
        PlayerSessionAdmissionStatus admissionStatus = Objects.requireNonNull(status, "status");
        return new byte[] {(byte) SCHEMA_VERSION, (byte) admissionStatus.wireId()};
    }

    public static PlayerSessionAdmissionStatus decode(byte[] payload)
            throws PlayerSessionAdmissionException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length != PAYLOAD_BYTES) {
            throw new PlayerSessionAdmissionException(
                    PlayerSessionAdmissionException.Code.INVALID_SIZE,
                    "player session admission payload has an invalid size");
        }
        int schemaVersion = Byte.toUnsignedInt(payload[0]);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new PlayerSessionAdmissionException(
                    PlayerSessionAdmissionException.Code.UNSUPPORTED_SCHEMA,
                    "player session admission schema is unsupported");
        }
        int statusCode = Byte.toUnsignedInt(payload[1]);
        return PlayerSessionAdmissionStatus.fromWireId(statusCode)
                .orElseThrow(
                        () ->
                                new PlayerSessionAdmissionException(
                                        PlayerSessionAdmissionException.Code.INVALID_STATUS,
                                        "player session admission status is invalid"));
    }
}
