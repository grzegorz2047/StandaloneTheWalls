package pl.grzegorz2047.standalonethewalls.server.realtime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.server.identity.session.AuthorizedPlayerSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.BouncyCastleDtls13Support;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.IssuedRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeChannelBindingDigest;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketContext;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketIdentity;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTransportCapability;

/** Process-owned bridge from an admitted reliable session to one-time realtime credentials. */
public final class RealtimeTicketProvisioner implements AutoCloseable {
    public static final int PROFILE_VERSION = 1;
    private static final RealtimeTransportCapability INJECTED_TRANSPORT_CAPABILITY =
            RealtimeTransportCapability.available("injected-realtime-transport", "composed");
    private static final RealtimeTransportCapability DISABLED_TRANSPORT_CAPABILITY =
            RealtimeTransportCapability.unavailable(
                    "realtime-transport",
                    "not-configured",
                    RealtimeTransportCapability.Reason.EXPLICITLY_DISABLED);

    private final OneTimeRealtimeTicketStore store;
    private final Duration lifetime;
    private final RealtimeTransportCapability transportCapability;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RealtimeTicketProvisioner(OneTimeRealtimeTicketStore store, Duration lifetime) {
        this(
                Objects.requireNonNull(store, "store"),
                requireLifetime(lifetime),
                INJECTED_TRANSPORT_CAPABILITY);
    }

    private RealtimeTicketProvisioner(
            OneTimeRealtimeTicketStore store,
            Duration lifetime,
            RealtimeTransportCapability transportCapability) {
        this.store = store;
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.transportCapability =
                Objects.requireNonNull(transportCapability, "transportCapability");
        if (transportCapability.available() != (store != null)) {
            throw new IllegalArgumentException(
                    "available transport requires a store and unavailable transport forbids one");
        }
    }

    public static RealtimeTicketProvisioner createProduction(
            int maximumActiveTickets, Duration lifetime) {
        return createProduction(
                maximumActiveTickets, lifetime, BouncyCastleDtls13Support.current());
    }

    public static RealtimeTicketProvisioner createProduction(
            int maximumActiveTickets,
            Duration lifetime,
            RealtimeTransportCapability transportCapability) {
        Duration boundedLifetime = requireLifetime(lifetime);
        RealtimeTicketStoreConfig configuration =
                new RealtimeTicketStoreConfig(maximumActiveTickets, boundedLifetime);
        RealtimeTransportCapability capability =
                Objects.requireNonNull(transportCapability, "transportCapability");
        if (!capability.available()) {
            return new RealtimeTicketProvisioner(null, boundedLifetime, capability);
        }
        return new RealtimeTicketProvisioner(
                OneTimeRealtimeTicketStore.createProduction(configuration),
                boundedLifetime,
                capability);
    }

    public static RealtimeTicketProvisioner disabled() {
        return new RealtimeTicketProvisioner(null, Duration.ZERO, DISABLED_TRANSPORT_CAPABILITY);
    }

    public boolean isTransportAvailable() {
        return transportCapability.available() && !closed.get();
    }

    public RealtimeTransportCapability capability() {
        return transportCapability;
    }

    public boolean isEnabled() {
        return store != null && isTransportAvailable();
    }

    public boolean supportsProfile(int profileVersion) {
        return isEnabled() && profileVersion == PROFILE_VERSION;
    }

    public IssuedRealtimeTicket issue(AuthorizedPlayerSession session, long roundEpoch)
            throws RealtimeTicketStoreException {
        AuthorizedPlayerSession authorized = Objects.requireNonNull(session, "session");
        OneTimeRealtimeTicketStore activeStore = store;
        if (activeStore == null || !isTransportAvailable() || !authorized.isOpen()) {
            throw new RealtimeTicketStoreException(RealtimeTicketStoreException.Code.CLOSED);
        }
        if (roundEpoch < 1L) {
            throw new IllegalArgumentException("roundEpoch must be positive");
        }

        byte[] channelBinding = authorized.channelBinding().bytes();
        byte[] digest = sha256(channelBinding);
        Arrays.fill(channelBinding, (byte) 0);
        try {
            return activeStore.issue(
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
        OneTimeRealtimeTicketStore activeStore = store;
        if (activeStore == null || !isTransportAvailable()) {
            throw new RealtimeTicketStoreException(RealtimeTicketStoreException.Code.CLOSED);
        }
        return activeStore.revoke(Objects.requireNonNull(identity, "identity"));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && store != null) {
            store.close();
        }
    }

    @Override
    public String toString() {
        return "RealtimeTicketProvisioner[enabled="
                + isEnabled()
                + ", capability="
                + transportCapability
                + ", secrets=redacted]";
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
