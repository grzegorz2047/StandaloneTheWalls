package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
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
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.server.identity.SessionIdentityAdmissionDecision;

class AuthenticatedPlayerAdmissionServiceTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId PLAYER_ID = new PlayerId("sf1_" + "a".repeat(52));
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "b".repeat(52));

    @Test
    void mapsEveryIdentityDecisionToOneStableAdmissionStatus() {
        FakeSession session = new FakeSession();

        for (SessionIdentityAdmissionDecision decision :
                SessionIdentityAdmissionDecision.values()) {
            AuthenticatedPlayerAdmissionService service =
                    new AuthenticatedPlayerAdmissionService((handle, playerId) -> decision);

            AuthenticatedPlayerAdmissionResult result = service.evaluate(session);

            assertThat(result.status()).isEqualTo(expectedStatus(decision));
            if (decision.isAccepted()) {
                assertThat(result).isInstanceOf(AuthenticatedPlayerAdmissionResult.Accepted.class);
                AuthenticatedPlayerAdmissionResult.Accepted accepted =
                        (AuthenticatedPlayerAdmissionResult.Accepted) result;
                assertThat(accepted.session().sessionId()).isEqualTo(session.sessionId());
                assertThat(accepted.session().serverId()).isEqualTo(SERVER_ID);
                assertThat(accepted.session().playerId()).isEqualTo(PLAYER_ID);
                assertThat(accepted.session().handle()).isEqualTo(HANDLE);
                assertThat(accepted.session().verificationLevel())
                        .isEqualTo(decision.verificationLevel().orElseThrow());
            } else {
                assertThat(result).isInstanceOf(AuthenticatedPlayerAdmissionResult.Rejected.class);
            }
        }
    }

    @Test
    void preservesAcceptedVerificationLevels() {
        FakeSession session = new FakeSession();
        AuthenticatedPlayerAdmissionService global =
                new AuthenticatedPlayerAdmissionService(
                        (handle, playerId) -> SessionIdentityAdmissionDecision.GLOBAL_ACCEPTED);
        AuthenticatedPlayerAdmissionService local =
                new AuthenticatedPlayerAdmissionService(
                        (handle, playerId) ->
                                SessionIdentityAdmissionDecision.LOCAL_FIRST_USE_ACCEPTED);

        AuthenticatedPlayerAdmissionResult.Accepted globalResult =
                (AuthenticatedPlayerAdmissionResult.Accepted) global.evaluate(session);
        AuthenticatedPlayerAdmissionResult.Accepted localResult =
                (AuthenticatedPlayerAdmissionResult.Accepted) local.evaluate(session);

        assertThat(globalResult.session().verificationLevel())
                .isEqualTo(HandleVerificationLevel.GLOBAL_VERIFIED);
        assertThat(localResult.session().verificationLevel())
                .isEqualTo(HandleVerificationLevel.LOCAL_UNVERIFIED);
    }

    private static PlayerSessionAdmissionStatus expectedStatus(
            SessionIdentityAdmissionDecision decision) {
        return switch (decision) {
            case GLOBAL_ACCEPTED -> PlayerSessionAdmissionStatus.GLOBAL_ACCEPTED;
            case LOCAL_FIRST_USE_ACCEPTED -> PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED;
            case LOCAL_RETURNING_ACCEPTED -> PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED;
            case PLAYER_BANNED -> PlayerSessionAdmissionStatus.PLAYER_BANNED;
            case REGISTRY_UNAVAILABLE -> PlayerSessionAdmissionStatus.REGISTRY_UNAVAILABLE;
            case REGISTRY_STALE -> PlayerSessionAdmissionStatus.REGISTRY_STALE;
            case UNKNOWN_GLOBAL_HANDLE -> PlayerSessionAdmissionStatus.UNKNOWN_GLOBAL_HANDLE;
            case REVOKED_GLOBAL_HANDLE -> PlayerSessionAdmissionStatus.REVOKED_GLOBAL_HANDLE;
            case GLOBAL_PLAYER_MISMATCH -> PlayerSessionAdmissionStatus.GLOBAL_PLAYER_MISMATCH;
            case LOCAL_BINDING_CONFLICT -> PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT;
            case LOCAL_BINDING_CAPACITY_EXCEEDED ->
                    PlayerSessionAdmissionStatus.LOCAL_BINDING_CAPACITY_EXCEEDED;
        };
    }

    private static final class FakeSession implements AuthenticatedPlayerSession {
        private final UUID sessionId = UUID.fromString("5bc72e98-f704-4f44-8f33-409f6e354355");
        private final StubReliableChannel channel = new StubReliableChannel();

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public ServerId serverId() {
            return SERVER_ID;
        }

        @Override
        public PlayerId playerId() {
            return PLAYER_ID;
        }

        @Override
        public CanonicalHandle handle() {
            return HANDLE;
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
