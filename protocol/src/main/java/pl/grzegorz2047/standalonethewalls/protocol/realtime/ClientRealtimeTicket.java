package pl.grzegorz2047.standalonethewalls.protocol.realtime;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Destroyable client ownership of one DTLS external-PSK credential. */
public final class ClientRealtimeTicket implements AutoCloseable {
    public static final int IDENTITY_BYTES = 16;
    public static final int PRE_SHARED_KEY_BYTES = 32;

    private final long requestId;
    private final int profileVersion;
    private final byte[] identity;
    private final byte[] preSharedKey;
    private final Instant expiresAt;
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final Object secretLock = new Object();

    public ClientRealtimeTicket(
            long requestId,
            int profileVersion,
            byte[] identity,
            byte[] preSharedKey,
            Instant expiresAt) {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        if (profileVersion < 1 || profileVersion > 0xFF) {
            throw new IllegalArgumentException("profileVersion must fit a positive unsigned byte");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(preSharedKey, "preSharedKey");
        if (identity.length != IDENTITY_BYTES) {
            throw new IllegalArgumentException("realtime identity must contain 16 bytes");
        }
        if (preSharedKey.length != PRE_SHARED_KEY_BYTES) {
            throw new IllegalArgumentException("realtime PSK must contain 32 bytes");
        }
        Instant expiration = Objects.requireNonNull(expiresAt, "expiresAt");
        if (expiration.toEpochMilli() < 1L) {
            throw new IllegalArgumentException("expiresAt must be after the Unix epoch");
        }
        this.requestId = requestId;
        this.profileVersion = profileVersion;
        this.identity = identity.clone();
        this.preSharedKey = preSharedKey.clone();
        this.expiresAt = expiration;
    }

    public long requestId() {
        return requestId;
    }

    public int profileVersion() {
        return profileVersion;
    }

    public byte[] copyIdentity() {
        return identity.clone();
    }

    public byte[] copyPreSharedKey() {
        synchronized (secretLock) {
            if (destroyed.get()) {
                throw new IllegalStateException("realtime PSK has been destroyed");
            }
            return preSharedKey.clone();
        }
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public void close() {
        synchronized (secretLock) {
            if (destroyed.compareAndSet(false, true)) {
                Arrays.fill(preSharedKey, (byte) 0);
            }
        }
    }

    @Override
    public String toString() {
        return "ClientRealtimeTicket[requestId="
                + requestId
                + ", profileVersion="
                + profileVersion
                + ", identity=opaque, preSharedKey=redacted, expiresAt="
                + expiresAt
                + ']';
    }
}
