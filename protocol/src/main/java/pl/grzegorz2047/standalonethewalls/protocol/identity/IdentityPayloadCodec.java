package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/** Strict big-endian v1 payload codec for the three identity message types. */
public final class IdentityPayloadCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int CHALLENGE_BYTES = 42;
    public static final int MAXIMUM_PROOF_BYTES = 414;
    public static final int MAXIMUM_RESULT_BYTES = 70;

    private static final int PLAYER_ID_BYTES = 56;
    private static final int MAXIMUM_PUBLIC_KEY_BYTES = 256;
    private static final int SIGNATURE_BYTES = 64;
    private static final int MAXIMUM_PUBLIC_CODE_BYTES = 64;
    private static final long MAXIMUM_EXPIRATION_EPOCH_MILLIS = 253_402_300_799_999L;

    private IdentityPayloadCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeChallenge(IdentityChallenge challenge) {
        Objects.requireNonNull(challenge, "challenge");
        return encodeChallenge(
                new IdentityChallengePayload(challenge.nonce(), challenge.expiresAt()));
    }

    public static byte[] encodeChallenge(IdentityChallengePayload payload) {
        Objects.requireNonNull(payload, "payload");
        long expiresAt = payload.expiresAt().toEpochMilli();
        if (expiresAt < 0L || expiresAt > MAXIMUM_EXPIRATION_EPOCH_MILLIS) {
            throw new IllegalArgumentException("challenge expiration is outside the wire range");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(CHALLENGE_BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeShort(SCHEMA_VERSION);
            output.write(payload.nonce());
            output.writeLong(expiresAt);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("in-memory challenge encoding failed", exception);
        }
    }

    public static IdentityChallengePayload decodeChallenge(byte[] encoded)
            throws IdentityPayloadException {
        byte[] payload = requirePayload(encoded, CHALLENGE_BYTES, CHALLENGE_BYTES);
        try {
            DataInputStream input = input(payload);
            requireSchema(input);
            byte[] nonce = readExact(input, IdentityChallenge.NONCE_BYTES);
            long expiresAt = input.readLong();
            requireEnd(input);
            if (expiresAt < 0L || expiresAt > MAXIMUM_EXPIRATION_EPOCH_MILLIS) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.INVALID_EXPIRATION,
                        "identity challenge expiration is outside the supported range");
            }
            try {
                return new IdentityChallengePayload(nonce, Instant.ofEpochMilli(expiresAt));
            } catch (DateTimeException | IllegalArgumentException exception) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.INVALID_EXPIRATION,
                        "identity challenge expiration is invalid",
                        exception);
            }
        } catch (EOFException exception) {
            throw truncated(exception);
        } catch (IOException exception) {
            throw decodingFailure(exception);
        }
    }

    public static byte[] encodeProof(IdentityProof proof) {
        Objects.requireNonNull(proof, "proof");
        byte[] handle = ascii(proof.handle().value());
        byte[] playerId = ascii(proof.playerId().value());
        byte[] publicKey = proof.publicKey();
        byte[] signature = proof.signature();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAXIMUM_PROOF_BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeShort(SCHEMA_VERSION);
            output.writeShort(proof.protocolVersion().major());
            output.writeShort(proof.protocolVersion().minor());
            writeField(output, handle);
            writeField(output, playerId);
            writeField(output, publicKey);
            writeField(output, signature);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("in-memory proof encoding failed", exception);
        }
    }

    public static IdentityProof decodeProof(byte[] encoded) throws IdentityPayloadException {
        byte[] payload = requirePayload(encoded, 1, MAXIMUM_PROOF_BYTES);
        try {
            DataInputStream input = input(payload);
            requireSchema(input);
            ProtocolVersion version =
                    new ProtocolVersion(input.readUnsignedShort(), input.readUnsignedShort());
            String handleText = readAsciiField(input, 3, 24, "handle");
            String playerIdText =
                    readAsciiField(input, PLAYER_ID_BYTES, PLAYER_ID_BYTES, "playerId");
            byte[] publicKey = readField(input, 1, MAXIMUM_PUBLIC_KEY_BYTES, "public key");
            byte[] signature = readField(input, SIGNATURE_BYTES, SIGNATURE_BYTES, "signature");
            requireEnd(input);

            try {
                IdentityKeys.decodePublicKey(publicKey);
            } catch (IdentityException exception) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.INVALID_PUBLIC_KEY,
                        "identity proof public key is invalid",
                        exception);
            }

            try {
                return new IdentityProof(
                        version,
                        new CanonicalHandle(handleText),
                        new PlayerId(playerIdText),
                        publicKey,
                        signature);
            } catch (IllegalArgumentException exception) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.INVALID_TEXT,
                        "identity proof contains an invalid canonical value",
                        exception);
            }
        } catch (EOFException exception) {
            throw truncated(exception);
        } catch (IOException exception) {
            throw decodingFailure(exception);
        }
    }

    public static byte[] encodeResult(IdentityResultPayload payload) {
        Objects.requireNonNull(payload, "payload");
        byte[] code = ascii(payload.publicCode());
        if (code.length < 1 || code.length > MAXIMUM_PUBLIC_CODE_BYTES) {
            throw new IllegalArgumentException(
                    "identity result public code is outside the wire range");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAXIMUM_RESULT_BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeShort(SCHEMA_VERSION);
            output.writeShort(payload.status().wireId());
            writeField(output, code);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("in-memory result encoding failed", exception);
        }
    }

    public static IdentityResultPayload decodeResult(byte[] encoded)
            throws IdentityPayloadException {
        byte[] payload = requirePayload(encoded, 1, MAXIMUM_RESULT_BYTES);
        try {
            DataInputStream input = input(payload);
            requireSchema(input);
            int wireId = input.readUnsignedShort();
            IdentityResultStatus status =
                    IdentityResultStatus.fromWireId(wireId)
                            .orElseThrow(
                                    () ->
                                            new IdentityPayloadException(
                                                    IdentityPayloadException.Code.INVALID_STATUS,
                                                    "identity result status is unknown"));
            String publicCode = readAsciiField(input, 1, MAXIMUM_PUBLIC_CODE_BYTES, "public code");
            requireEnd(input);
            if (!status.publicCode().equals(publicCode)) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.STATUS_CODE_MISMATCH,
                        "identity result status and public code do not match");
            }
            return new IdentityResultPayload(status);
        } catch (EOFException exception) {
            throw truncated(exception);
        } catch (IOException exception) {
            throw decodingFailure(exception);
        }
    }

    private static DataInputStream input(byte[] payload) {
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static void requireSchema(DataInputStream input)
            throws IOException, IdentityPayloadException {
        int schema = input.readUnsignedShort();
        if (schema != SCHEMA_VERSION) {
            throw new IdentityPayloadException(
                    IdentityPayloadException.Code.UNSUPPORTED_SCHEMA,
                    "identity payload schema is unsupported");
        }
    }

    private static byte[] requirePayload(byte[] encoded, int minimum, int maximum)
            throws IdentityPayloadException {
        byte[] payload = Objects.requireNonNull(encoded, "encoded").clone();
        if (payload.length < minimum || payload.length > maximum) {
            throw new IdentityPayloadException(
                    IdentityPayloadException.Code.INVALID_SIZE,
                    "identity payload size is outside the allowed range");
        }
        return payload;
    }

    private static void writeField(DataOutputStream output, byte[] value) throws IOException {
        output.writeShort(value.length);
        output.write(value);
    }

    private static byte[] readField(DataInputStream input, int minimum, int maximum, String field)
            throws IOException, IdentityPayloadException {
        int length = input.readUnsignedShort();
        if (length < minimum || length > maximum) {
            throw new IdentityPayloadException(
                    IdentityPayloadException.Code.INVALID_LENGTH,
                    "identity " + field + " length is outside the allowed range");
        }
        return readExact(input, length);
    }

    private static String readAsciiField(
            DataInputStream input, int minimum, int maximum, String field)
            throws IOException, IdentityPayloadException {
        byte[] value = readField(input, minimum, maximum, field);
        for (byte element : value) {
            if ((element & 0x80) != 0) {
                throw new IdentityPayloadException(
                        IdentityPayloadException.Code.INVALID_TEXT,
                        "identity " + field + " must contain canonical ASCII");
            }
        }
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static byte[] readExact(DataInputStream input, int length) throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("identity payload ended inside a field");
        }
        return value;
    }

    private static void requireEnd(DataInputStream input)
            throws IOException, IdentityPayloadException {
        if (input.available() != 0) {
            throw new IdentityPayloadException(
                    IdentityPayloadException.Code.TRAILING_BYTES,
                    "identity payload contains trailing bytes");
        }
    }

    private static byte[] ascii(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        if (!value.equals(new String(encoded, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("identity wire text must be canonical ASCII");
        }
        return encoded;
    }

    private static IdentityPayloadException truncated(EOFException cause) {
        return new IdentityPayloadException(
                IdentityPayloadException.Code.INVALID_SIZE,
                "identity payload ended before all fields were available",
                cause);
    }

    private static IdentityPayloadException decodingFailure(IOException cause) {
        return new IdentityPayloadException(
                IdentityPayloadException.Code.INVALID_SIZE,
                "identity payload could not be decoded",
                cause);
    }
}
