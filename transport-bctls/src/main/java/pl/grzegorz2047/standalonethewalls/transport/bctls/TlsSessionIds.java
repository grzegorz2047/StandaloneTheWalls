package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/** Generates injectable RFC 4122 UUIDv4 identifiers for logical transport sessions. */
final class TlsSessionIds {
    private TlsSessionIds() {
        throw new AssertionError("No instances");
    }

    static UUID randomV4(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID sessionId = new UUID(buffer.getLong(), buffer.getLong());
        if (!TlsSessionBootstrapCodec.isValidSessionId(sessionId)) {
            throw new AssertionError("generated session UUID is invalid");
        }
        return sessionId;
    }
}
