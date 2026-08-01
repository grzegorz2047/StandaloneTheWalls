package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** One server-issued, session-bound, time-limited authentication challenge. */
public final class IdentityChallenge {
    public static final int NONCE_BYTES = 32;
    private static final Pattern SERVER_ID = Pattern.compile("[a-z0-9._:-]{3,128}");

    private final String serverId;
    private final UUID sessionId;
    private final byte[] nonce;
    private final Instant expiresAt;

    public IdentityChallenge(String serverId, UUID sessionId, byte[] nonce, Instant expiresAt) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!SERVER_ID.matcher(serverId).matches()) {
            throw new IllegalArgumentException("invalid serverId");
        }
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must contain exactly 32 bytes");
        }
    }

    public String serverId() {
        return serverId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "IdentityChallenge[serverId="
                + serverId
                + ", sessionId="
                + sessionId
                + ", expiresAt="
                + expiresAt
                + ']';
    }
}
