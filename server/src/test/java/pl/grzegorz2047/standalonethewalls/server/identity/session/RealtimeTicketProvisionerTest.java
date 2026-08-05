package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.server.realtime.RealtimeTicketProvisioner;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.IssuedRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketEntropy;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketRedemption;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTransportCapability;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RedeemedRealtimeTicket;

class RealtimeTicketProvisionerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T06:00:00Z");

    @Test
    void derivesEveryTrustedContextFieldFromTheAuthorizedSession()
            throws RealtimeTicketStoreException, NoSuchAlgorithmException {
        byte[] channelBinding = filled(SecureChannelBinding.BYTES, 7);
        QueueEntropy entropy = new QueueEntropy(filled(16, 8), filled(32, 9));
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        entropy,
                        new RealtimeTicketStoreConfig(2, Duration.ofSeconds(30)));
        RealtimeTicketProvisioner provisioner =
                new RealtimeTicketProvisioner(store, Duration.ofSeconds(20));
        TestSession transport = new TestSession(channelBinding);
        AuthorizedPlayerSession session =
                new AuthorizedPlayerSession(transport, HandleVerificationLevel.LOCAL_UNVERIFIED);

        IssuedRealtimeTicket issued = provisioner.issue(session, 4L);
        RealtimeTicketRedemption redemption = store.redeem(issued.identity());

        assertThat(redemption.status()).isEqualTo(RealtimeTicketRedemption.Status.REDEEMED);
        try (RedeemedRealtimeTicket redeemed = redemption.ticket().orElseThrow()) {
            assertThat(redeemed.context().serverId()).isEqualTo(transport.serverId());
            assertThat(redeemed.context().reliableSessionId()).isEqualTo(transport.sessionId());
            assertThat(redeemed.context().playerId()).isEqualTo(transport.playerId());
            assertThat(redeemed.context().roundEpoch()).isEqualTo(4L);
            assertThat(redeemed.context().channelBindingDigest().copyBytes())
                    .containsExactly(MessageDigest.getInstance("SHA-256").digest(channelBinding));
            assertThat(redeemed.preSharedKey().copyBytes()).containsOnly(9);
        }
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(issued.toString()).contains("redacted").doesNotContain("09090909");

        issued.close();
        provisioner.close();
    }

    @Test
    void explicitlyReviewedCapabilityEnablesTheProductionStore() {
        RealtimeTicketProvisioner provisioner =
                RealtimeTicketProvisioner.createProduction(
                        2,
                        Duration.ofSeconds(20),
                        RealtimeTransportCapability.available("reviewed-provider", "1.0"));

        assertThat(provisioner.isTransportAvailable()).isTrue();
        assertThat(provisioner.isEnabled()).isTrue();
        assertThat(provisioner.supportsProfile(RealtimeTicketProvisioner.PROFILE_VERSION)).isTrue();

        provisioner.close();
        assertThat(provisioner.isTransportAvailable()).isFalse();
    }

    @Test
    void productionFactoryFailsClosedWhenTheReviewedProviderCannotOfferDtls13() {
        RealtimeTicketProvisioner provisioner =
                RealtimeTicketProvisioner.createProduction(2, Duration.ofSeconds(20));

        assertThat(provisioner.isTransportAvailable()).isFalse();
        assertThat(provisioner.isEnabled()).isFalse();
        assertThat(provisioner.supportsProfile(RealtimeTicketProvisioner.PROFILE_VERSION))
                .isFalse();
        assertThat(provisioner.capability().reason())
                .isEqualTo(RealtimeTransportCapability.Reason.DTLS_1_3_NOT_IMPLEMENTED);
        assertThat(provisioner.toString()).contains("redacted").doesNotContain("psk");

        provisioner.close();
        provisioner.close();
    }

    @Test
    void revokeAndCloseFailClosedWithoutExposingStoreDetails() throws RealtimeTicketStoreException {
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new QueueEntropy(filled(16, 10), filled(32, 11)),
                        new RealtimeTicketStoreConfig(1, Duration.ofSeconds(30)));
        RealtimeTicketProvisioner provisioner =
                new RealtimeTicketProvisioner(store, Duration.ofSeconds(20));
        AuthorizedPlayerSession session =
                new AuthorizedPlayerSession(
                        new TestSession(filled(32, 12)), HandleVerificationLevel.LOCAL_UNVERIFIED);
        IssuedRealtimeTicket issued = provisioner.issue(session, 1L);

        assertThat(provisioner.revoke(issued.identity())).isTrue();
        assertThat(store.redeem(issued.identity()).status())
                .isEqualTo(RealtimeTicketRedemption.Status.UNKNOWN_OR_REPLAYED);

        provisioner.close();
        provisioner.close();
        issued.close();

        assertThat(provisioner.isEnabled()).isFalse();
        assertThat(provisioner.toString()).contains("redacted");
        assertThatThrownBy(() -> provisioner.issue(session, 2L))
                .isInstanceOf(RealtimeTicketStoreException.class)
                .extracting(exception -> ((RealtimeTicketStoreException) exception).code())
                .isEqualTo(RealtimeTicketStoreException.Code.CLOSED);
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static final class QueueEntropy implements RealtimeTicketEntropy {
        private final Queue<byte[]> values;

        private QueueEntropy(byte[]... values) {
            this.values = new java.util.ArrayDeque<>();
            Arrays.stream(values).forEach(value -> this.values.add(value.clone()));
        }

        @Override
        public byte[] randomBytes(int length) {
            byte[] value = values.remove();
            if (value.length != length) {
                throw new IllegalStateException("test entropy length mismatch");
            }
            return value.clone();
        }
    }

    private static final class TestSession implements AuthenticatedPlayerSession {
        private final UUID sessionId = UUID.fromString("123e4567-e89b-42d3-a456-426614174123");
        private final ServerId serverId = new ServerId("sfs1_" + "a".repeat(52));
        private final PlayerId playerId = new PlayerId("sf1_" + "b".repeat(52));
        private final CanonicalHandle handle = new CanonicalHandle("ticket_tester");
        private final SecureChannelBinding channelBinding;
        private final StubReliableChannel channel = new StubReliableChannel();

        private TestSession(byte[] channelBinding) {
            this.channelBinding = new SecureChannelBinding(channelBinding);
        }

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public ServerId serverId() {
            return serverId;
        }

        @Override
        public PlayerId playerId() {
            return playerId;
        }

        @Override
        public SecureChannelBinding channelBinding() {
            return channelBinding;
        }

        @Override
        public CanonicalHandle handle() {
            return handle;
        }

        @Override
        public ReliableChannel reliableChannel() {
            return channel;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return channel.close();
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final AtomicBoolean open = new AtomicBoolean(true);

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            return CompletableFuture.completedFuture(new ReliableSendResult(0L));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            open.set(false);
            return CompletableFuture.completedFuture(null);
        }
    }
}
