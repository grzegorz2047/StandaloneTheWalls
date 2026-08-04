package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded process-local store for one-time DTLS external-PSK tickets.
 *
 * <p>Redemption removes a ticket before returning its secret. A failed future handshake must not
 * restore the ticket; a fresh ticket requires the authenticated reliable channel.
 */
public final class OneTimeRealtimeTicketStore implements AutoCloseable {
    private static final int MAXIMUM_IDENTITY_GENERATION_ATTEMPTS = 8;

    private final Clock clock;
    private final RealtimeTicketEntropy entropy;
    private final RealtimeTicketStoreConfig config;
    private final Map<RealtimeTicketIdentity, StoredTicket> tickets = new HashMap<>();
    private boolean closed;

    public OneTimeRealtimeTicketStore(
            Clock clock, RealtimeTicketEntropy entropy, RealtimeTicketStoreConfig config) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entropy = Objects.requireNonNull(entropy, "entropy");
        this.config = Objects.requireNonNull(config, "config");
    }

    public static OneTimeRealtimeTicketStore createProduction(
            RealtimeTicketStoreConfig config) {
        return new OneTimeRealtimeTicketStore(
                Clock.systemUTC(), new SecureRealtimeTicketEntropy(), config);
    }

    public synchronized IssuedRealtimeTicket issue(
            RealtimeTicketContext context, Duration lifetime)
            throws RealtimeTicketStoreException {
        requireOpen();
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero()
                || lifetime.isNegative()
                || lifetime.compareTo(config.maximumLifetime()) > 0) {
            throw new RealtimeTicketStoreException(
                    RealtimeTicketStoreException.Code.INVALID_LIFETIME);
        }

        Instant now = clock.instant();
        removeExpired(now);
        if (tickets.size() >= config.maximumActiveTickets()) {
            throw new RealtimeTicketStoreException(
                    RealtimeTicketStoreException.Code.CAPACITY_EXHAUSTED);
        }

        Instant expiresAt;
        try {
            expiresAt = now.plus(lifetime);
        } catch (ArithmeticException | DateTimeException exception) {
            throw new RealtimeTicketStoreException(
                    RealtimeTicketStoreException.Code.INVALID_LIFETIME);
        }

        RealtimeTicketIdentity identity = generateUniqueIdentity();
        byte[] keyBytes = randomBytes(RealtimePreSharedKey.LENGTH_BYTES);
        try {
            tickets.put(identity, new StoredTicket(keyBytes, context, expiresAt));
            return new IssuedRealtimeTicket(
                    identity, new RealtimePreSharedKey(keyBytes), expiresAt);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public synchronized RealtimeTicketRedemption redeem(RealtimeTicketIdentity identity)
            throws RealtimeTicketStoreException {
        requireOpen();
        Objects.requireNonNull(identity, "identity");
        StoredTicket stored = tickets.remove(identity);
        if (stored == null) {
            return RealtimeTicketRedemption.unknownOrReplayed();
        }

        Instant now = clock.instant();
        if (!now.isBefore(stored.expiresAt())) {
            stored.destroy();
            return RealtimeTicketRedemption.expired();
        }

        byte[] keyBytes = stored.takeKeyBytes();
        try {
            return RealtimeTicketRedemption.redeemed(
                    new RedeemedRealtimeTicket(
                            identity,
                            new RealtimePreSharedKey(keyBytes),
                            stored.context(),
                            stored.expiresAt()));
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public synchronized int removeExpired() throws RealtimeTicketStoreException {
        requireOpen();
        return removeExpired(clock.instant());
    }

    public synchronized int activeTicketCount() throws RealtimeTicketStoreException {
        requireOpen();
        removeExpired(clock.instant());
        return tickets.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (StoredTicket ticket : tickets.values()) {
            ticket.destroy();
        }
        tickets.clear();
    }

    private RealtimeTicketIdentity generateUniqueIdentity()
            throws RealtimeTicketStoreException {
        for (int attempt = 0; attempt < MAXIMUM_IDENTITY_GENERATION_ATTEMPTS; attempt++) {
            RealtimeTicketIdentity identity =
                    new RealtimeTicketIdentity(randomBytes(RealtimeTicketIdentity.LENGTH_BYTES));
            if (!tickets.containsKey(identity)) {
                return identity;
            }
        }
        throw new RealtimeTicketStoreException(
                RealtimeTicketStoreException.Code.IDENTITY_COLLISION_LIMIT);
    }

    private byte[] randomBytes(int expectedLength) throws RealtimeTicketStoreException {
        byte[] bytes;
        try {
            bytes = entropy.randomBytes(expectedLength);
        } catch (RuntimeException exception) {
            throw new RealtimeTicketStoreException(
                    RealtimeTicketStoreException.Code.INVALID_ENTROPY);
        }
        if (bytes == null || bytes.length != expectedLength) {
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
            throw new RealtimeTicketStoreException(
                    RealtimeTicketStoreException.Code.INVALID_ENTROPY);
        }
        return bytes;
    }

    private int removeExpired(Instant now) {
        int removed = 0;
        Iterator<Map.Entry<RealtimeTicketIdentity, StoredTicket>> iterator =
                tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredTicket ticket = iterator.next().getValue();
            if (!now.isBefore(ticket.expiresAt())) {
                ticket.destroy();
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private void requireOpen() throws RealtimeTicketStoreException {
        if (closed) {
            throw new RealtimeTicketStoreException(RealtimeTicketStoreException.Code.CLOSED);
        }
    }

    private static final class StoredTicket {
        private final byte[] keyBytes;
        private final RealtimeTicketContext context;
        private final Instant expiresAt;
        private boolean destroyed;

        private StoredTicket(
                byte[] keyBytes, RealtimeTicketContext context, Instant expiresAt) {
            this.keyBytes = keyBytes.clone();
            this.context = context;
            this.expiresAt = expiresAt;
        }

        private RealtimeTicketContext context() {
            return context;
        }

        private Instant expiresAt() {
            return expiresAt;
        }

        private byte[] takeKeyBytes() {
            if (destroyed) {
                throw new IllegalStateException("ticket key has been destroyed");
            }
            byte[] copy = keyBytes.clone();
            destroy();
            return copy;
        }

        private void destroy() {
            if (!destroyed) {
                Arrays.fill(keyBytes, (byte) 0);
                destroyed = true;
            }
        }
    }
}
