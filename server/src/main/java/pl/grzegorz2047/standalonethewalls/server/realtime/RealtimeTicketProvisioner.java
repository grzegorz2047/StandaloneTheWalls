package pl.grzegorz2047.standalonethewalls.server.realtime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.IssuedRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeChannelBindingDigest;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketContext;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketIdentity;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;

/** Process-owned bridge from an admitted reliable session to one-time realtime credentials. */
public final class RealtimeTicketProvisioner implements AutoCloseable {
    public static final int PROFILE_VERSION = 1;

    private final OneTimeRealtimeTicketStore store;
    private final Duration lifetime;
    private final boolean enabled;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RealtimeTicketProvisioner(OneTimeRealtimeTicketStore store, Duration lifetime) {
        this.store = Objects.requireNonNull(store, "store");
        this.lifetime = requireLifetime(lifetime);
        enabled = true;
    }

    private RealtimeTicketProvisioner() {
        store = null;
        lifetime = Duration.ZERO;
        enabled = false;
    }

    public static RealtimeTicketProvisioner createProduction(
            int maximumActiveTickets, Duration lifetime) {
        Duration boundedLifetime = requireLifetime(lifetime);
        return new RealtimeTicketProvisioner(
                OneTimeRealtimeTicketStore.createProduction(
                        new RealtimeTicketStoreConfig(maximumActiveTickets, boundedLifetime)),
                boundedLifetime);
    }

    public static RealtimeTicketProvisioner disabled() {
        return new RealtimeTicketProvisioner();
    }

    public boolean isEnabled() {
        return enabled && !closed.get();
    }

    public boolean supportsProfile(int profileVersion) {
        return isEnabled() && profileVersion == PROFILE_VERSION;
    }

    public IssuedRealtimeTicket issue(AuthorizedPlayerSession session, long roundEpoch)
            throws RealtimeTicketStoreException {
        AuthorizedPlayerSession authorized = Objects.requireNonNull(session, "session");
        if (!isEnabled() || !authorized.isOpen()) {
            throw new RealtimeTicketStoreException(RealtimeTicketStoreException.Code.CLOSED);
        }
        if (roundEpoch < 1L) {
            throw new IllegalArgumentException("roundEpoch must be positive");
        }

        byte[] channelBinding = authorized.channelBinding().bytes();
        byte[] digest = sha256(channelBinding);
        Arrays.fill(channelBinding, (byte) 0);
        try {
            return store.issue(
                    new RealtimeTicketContext(
                            authorized.serverId(),
                            authorized.sessionId(),
                            authorized.playerId(),
                            new RealtimeChannelBindingDigest(digest),
                            roundEpoch),
                    lifetime);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    public boolean revoke(RealtimeTicketIdentity identity) throws RealtimeTicketStoreException {
        if (!isEnabled()) {
            throw new RealtimeTicketStoreException(RealtimeTicketStoreException.Code.CLOSED);
        }
        return store.revoke(Objects.requireNonNull(identity, "identity"));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && store != null) {
            store.close();
        }
    }

    @Override
    public String toString() {
        return "RealtimeTicketProvisioner[enabled=" + isEnabled() + ", secrets=redacted]";
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Duration requireLifetime(Duration lifetime) {
        Duration value = Objects.requireNonNull(lifetime, "lifetime");
        if (value.isZero()
                || value.isNegative()
                || value.compareTo(RealtimeTicketStoreConfig.HARD_MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException("realtime ticket lifetime is outside hard bounds");
        }
        return value;
    }
}
