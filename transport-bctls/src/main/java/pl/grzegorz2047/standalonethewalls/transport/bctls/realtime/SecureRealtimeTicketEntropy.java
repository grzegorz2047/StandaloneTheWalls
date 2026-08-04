package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.security.SecureRandom;
import java.util.Objects;

/** Process-owned cryptographic entropy for production ticket generation. */
public final class SecureRealtimeTicketEntropy implements RealtimeTicketEntropy {
    private final SecureRandom secureRandom;

    public SecureRealtimeTicketEntropy() {
        this(new SecureRandom());
    }

    SecureRealtimeTicketEntropy(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public byte[] randomBytes(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
