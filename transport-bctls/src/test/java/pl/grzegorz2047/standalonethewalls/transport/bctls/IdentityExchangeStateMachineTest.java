package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityPayloadCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultPayload;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityResultStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

class IdentityExchangeStateMachineTest {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final ServerId SERVER_ID =
            new ServerId("sfs1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua");

    @Test
    void clientRejectsResultBeforeChallengeAndClosesSession()
            throws IdentityException, InterruptedException, TimeoutException {
        ScriptedChannel channel = new ScriptedChannel();
        channel.enqueue(
                envelope(
                        MessageType.IDENTITY_RESULT,
                        0L,
                        IdentityPayloadCodec.encodeResult(
                                new IdentityResultPayload(IdentityResultStatus.ACCEPTED))));
        BootstrappedReliableSession session = session(channel);
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());

        IdentityExchangeException failure =
                awaitFailure(
                        IdentityExchange.authenticateClient(
                                session,
                                identity,
                                new CanonicalHandle("player_one"),
                                Clock.systemUTC(),
                                IdentityExchangeConfig.DEFAULT));

        assertThat(failure.code()).isEqualTo(IdentityExchangeException.Code.UNEXPECTED_MESSAGE);
        assertThat(channel.closeCount()).isEqualTo(1);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void timeoutClosesSessionAndASecondExchangeCannotStart()
            throws IdentityException, InterruptedException, TimeoutException {
        ScriptedChannel channel = new ScriptedChannel();
        channel.enqueuePending();
        BootstrappedReliableSession session = session(channel);
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityExchangeConfig shortConfig =
                new IdentityExchangeConfig(
                        Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(50));

        IdentityExchangeException first =
                awaitFailure(
                        IdentityExchange.authenticateClient(
                                session,
                                identity,
                                new CanonicalHandle("player_one"),
                                Clock.systemUTC(),
                                shortConfig));
        IdentityExchangeException second =
                awaitFailure(
                        IdentityExchange.authenticateClient(
                                session,
                                identity,
                                new CanonicalHandle("player_one"),
                                Clock.systemUTC(),
                                shortConfig));

        assertThat(first.code()).isEqualTo(IdentityExchangeException.Code.TIMEOUT);
        assertThat(second.code())
                .isEqualTo(IdentityExchangeException.Code.EXCHANGE_ALREADY_STARTED);
        assertThat(channel.closeCount()).isEqualTo(1);
    }

    @Test
    void postAuthenticationChannelRejectsFurtherIdentityMessages()
            throws IdentityException, InterruptedException, TimeoutException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");

        ScriptedChannel sendDelegate = new ScriptedChannel();
        AuthenticatedReliableSession sendSession =
                new AuthenticatedReliableSession(
                        session(sendDelegate), identity.playerId(), handle);
        IdentityExchangeException sendFailure =
                awaitFailure(
                        sendSession
                                .reliableChannel()
                                .send(MessageType.IDENTITY_PROOF, new byte[0]));
        assertThat(sendFailure.code())
                .isEqualTo(IdentityExchangeException.Code.POST_AUTH_IDENTITY_MESSAGE);
        assertThat(sendDelegate.closeCount()).isEqualTo(1);

        ScriptedChannel receiveDelegate = new ScriptedChannel();
        receiveDelegate.enqueue(envelope(MessageType.IDENTITY_CHALLENGE, 0L, new byte[0]));
        AuthenticatedReliableSession receiveSession =
                new AuthenticatedReliableSession(
                        session(receiveDelegate), identity.playerId(), handle);
        IdentityExchangeException receiveFailure =
                awaitFailure(receiveSession.reliableChannel().receive());
        assertThat(receiveFailure.code())
                .isEqualTo(IdentityExchangeException.Code.POST_AUTH_IDENTITY_MESSAGE);
        assertThat(receiveDelegate.closeCount()).isEqualTo(1);
    }

    private static BootstrappedReliableSession session(ReliableChannel channel) {
        byte[] binding = new byte[SecureChannelBinding.BYTES];
        for (int index = 0; index < binding.length; index++) {
            binding[index] = (byte) (index + 1);
        }
        return new BootstrappedReliableSession(
                SESSION_ID,
                new Tls13SessionSecurity(
                        SERVER_ID,
                        new SecureChannelBinding(binding),
                        "TLS_AES_128_GCM_SHA256",
                        "sunderfront/1"),
                channel);
    }

    private static ProtocolEnvelope envelope(
            MessageType messageType, long sequence, byte[] payload) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT, messageType, SESSION_ID, sequence, payload);
    }

    private static <T> IdentityExchangeException awaitFailure(CompletionStage<T> stage)
            throws InterruptedException, TimeoutException {
        try {
            stage.toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            throw new AssertionError("expected identity exchange failure");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IdentityExchangeException identityFailure) {
                return identityFailure;
            }
            throw new AssertionError("unexpected failure type", cause);
        }
    }

    private static final class ScriptedChannel implements ReliableChannel {
        private final Queue<CompletionStage<Optional<ProtocolEnvelope>>> receives =
                new ArrayDeque<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger closeCount = new AtomicInteger();
        private long nextSequence;

        void enqueue(ProtocolEnvelope envelope) {
            receives.add(CompletableFuture.completedFuture(Optional.of(envelope)));
        }

        void enqueuePending() {
            receives.add(new CompletableFuture<>());
        }

        int closeCount() {
            return closeCount.get();
        }

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            if (!open.get()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("scripted channel is closed"));
            }
            return CompletableFuture.completedFuture(new ReliableSendResult(nextSequence++));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            CompletionStage<Optional<ProtocolEnvelope>> next = receives.poll();
            if (next == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return next;
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            if (open.compareAndSet(true, false)) {
                closeCount.incrementAndGet();
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
