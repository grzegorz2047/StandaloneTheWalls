package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.time.Instant;
import java.util.Objects;

/** Peer-visible challenge fields; transport context is supplied locally on each endpoint. */
public final class IdentityChallengePayload {
    private final byte[] nonce;
    private final Instant expiresAt;

    public IdentityChallengePayload(byte[] nonce, Instant expiresAt) {
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (nonce.length != IdentityChallenge.NONCE_BYTES) {
            throw new IllegalArgumentException("identity challenge nonce must contain 32 bytes");
        }
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "IdentityChallengePayload[nonceBytes="
                + nonce.length
                + ", expiresAt="
                + expiresAt
                + ']';
    }
}
