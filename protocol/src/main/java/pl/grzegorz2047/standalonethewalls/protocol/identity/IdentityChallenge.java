package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One server-issued, channel-bound, session-bound, time-limited authentication challenge. */
public final class IdentityChallenge {
    public static final int NONCE_BYTES = 32;

    private final ServerId serverId;
    private final UUID sessionId;
    private final byte[] nonce;
    private final SecureChannelBinding channelBinding;
    private final Instant expiresAt;

    public IdentityChallenge(
            ServerId serverId,
            UUID sessionId,
            byte[] nonce,
            SecureChannelBinding channelBinding,
            Instant expiresAt) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.channelBinding = Objects.requireNonNull(channelBinding, "channelBinding");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must contain exactly 32 bytes");
        }
    }

    public ServerId serverId() {
        return serverId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public SecureChannelBinding channelBinding() {
        return channelBinding;
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
                + ", channelBindingBytes="
                + SecureChannelBinding.BYTES
                + ", expiresAt="
                + expiresAt
                + ']';
    }
}
