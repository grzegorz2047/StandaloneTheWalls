package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Destroyable 256-bit external PSK. Public text never exposes key bytes. */
public final class RealtimePreSharedKey implements AutoCloseable {
    public static final int LENGTH_BYTES = 32;

    private final byte[] bytes;
    private final AtomicBoolean destroyed = new AtomicBoolean();

    public RealtimePreSharedKey(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH_BYTES) {
            throw new IllegalArgumentException("realtime PSK must contain 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    public byte[] copyBytes() {
        synchronized (bytes) {
            if (destroyed.get()) {
                throw new IllegalStateException("realtime PSK has been destroyed");
            }
            return bytes.clone();
        }
    }

    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public void close() {
        synchronized (bytes) {
            if (destroyed.compareAndSet(false, true)) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    @Override
    public String toString() {
        return "RealtimePreSharedKey[redacted]";
    }
}
