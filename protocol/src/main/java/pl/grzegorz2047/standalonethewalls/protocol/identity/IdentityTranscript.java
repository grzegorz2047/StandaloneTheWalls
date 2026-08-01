package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/** Canonical, unambiguous bytes signed by the client for authentication v1. */
public final class IdentityTranscript {
    private static final byte[] DOMAIN =
            "SUNDERFRONT-CLIENT-AUTH-V1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAXIMUM_FIELD_BYTES = 1024;

    private IdentityTranscript() {
        throw new AssertionError("No instances");
    }

    public static byte[] encode(
            ProtocolVersion version,
            IdentityChallenge challenge,
            CanonicalHandle handle,
            PlayerId playerId,
            byte[] publicKey) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(publicKey, "publicKey");

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            DataOutputStream output = new DataOutputStream(bytes);
            writeField(output, DOMAIN);
            output.writeShort(version.major());
            output.writeShort(version.minor());
            writeField(output, challenge.serverId().getBytes(StandardCharsets.US_ASCII));
            output.writeLong(challenge.sessionId().getMostSignificantBits());
            output.writeLong(challenge.sessionId().getLeastSignificantBits());
            writeField(output, challenge.nonce());
            writeField(output, handle.value().getBytes(StandardCharsets.US_ASCII));
            writeField(output, playerId.value().getBytes(StandardCharsets.US_ASCII));
            writeField(output, publicKey);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError("memory-backed transcript encoding failed", impossible);
        }
    }

    private static void writeField(DataOutputStream output, byte[] value) throws IOException {
        if (value.length > MAXIMUM_FIELD_BYTES) {
            throw new IllegalArgumentException("identity transcript field is too large");
        }
        output.writeInt(value.length);
        output.write(value);
    }
}
