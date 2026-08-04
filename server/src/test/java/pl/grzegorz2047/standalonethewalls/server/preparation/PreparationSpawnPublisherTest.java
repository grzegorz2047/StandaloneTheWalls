package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;

class PreparationSpawnPublisherTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void publishesExactlyOneEncodedAssignmentToEveryParticipantChannel()
            throws PreparationProtocolException {
        LobbyParticipantId alphaId = new LobbyParticipantId("alpha");
        LobbyParticipantId bravoId = new LobbyParticipantId("bravo");
        PreparationClientSpawn alpha = delivery(alphaId, LobbyTeam.GREEN, 2);
        PreparationClientSpawn bravo = delivery(bravoId, LobbyTeam.BLUE, 8);
        TestChannel alphaChannel = TestChannel.completed();
        TestChannel bravoChannel = TestChannel.completed();

        PreparationSpawnPublisher.publish(
                List.of(alpha, bravo),
                Map.of(alphaId, alphaChannel, bravoId, bravoChannel),
                TIMEOUT);

        assertAssignment(alphaChannel, alpha.assignment());
        assertAssignment(bravoChannel, bravo.assignment());
    }

    @Test
    void rejectsIncompleteExtraAndDuplicateCoverageBeforeAnySendStarts() {
        LobbyParticipantId alphaId = new LobbyParticipantId("alpha");
        LobbyParticipantId bravoId = new LobbyParticipantId("bravo");
        PreparationClientSpawn alpha = delivery(alphaId, LobbyTeam.GREEN, 2);
        PreparationClientSpawn bravo = delivery(bravoId, LobbyTeam.BLUE, 8);
        TestChannel alphaChannel = TestChannel.completed();
        TestChannel bravoChannel = TestChannel.completed();

        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(alpha, bravo), Map.of(alphaId, alphaChannel), TIMEOUT),
                PreparationSpawnPublishException.Code.CHANNEL_COVERAGE_MISMATCH);
        assertThat(alphaChannel.sent()).isEmpty();

        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(alpha),
                                Map.of(alphaId, alphaChannel, bravoId, bravoChannel),
                                TIMEOUT),
                PreparationSpawnPublishException.Code.CHANNEL_COVERAGE_MISMATCH);
        assertThat(alphaChannel.sent()).isEmpty();
        assertThat(bravoChannel.sent()).isEmpty();

        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(alpha, alpha), Map.of(alphaId, alphaChannel), TIMEOUT),
                PreparationSpawnPublishException.Code.DUPLICATE_PARTICIPANT);
        assertThat(alphaChannel.sent()).isEmpty();
    }

    @Test
    void rejectsEmptyPlanAndInvalidTimeout() {
        assertCode(
                () -> PreparationSpawnPublisher.publish(List.of(), Map.of(), TIMEOUT),
                PreparationSpawnPublishException.Code.EMPTY_PLAN);
        assertThatThrownBy(
                        () ->
                                PreparationSpawnPublisher.publish(
                                        List.of(delivery("alpha", LobbyTeam.GREEN, 2)),
                                        Map.of(
                                                new LobbyParticipantId("alpha"),
                                                TestChannel.completed()),
                                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsSendStartFailure() {
        LobbyParticipantId alphaId = new LobbyParticipantId("alpha");
        TestChannel channel = TestChannel.throwing();

        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(delivery(alphaId, LobbyTeam.GREEN, 2)),
                                Map.of(alphaId, channel),
                                TIMEOUT),
                PreparationSpawnPublishException.Code.SEND_START_FAILED);
        assertThat(channel.sent()).hasSize(1);
    }

    @Test
    void reportsExceptionalAndMissingSendResults() {
        LobbyParticipantId alphaId = new LobbyParticipantId("alpha");
        TestChannel failed = TestChannel.failed();
        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(delivery(alphaId, LobbyTeam.GREEN, 2)),
                                Map.of(alphaId, failed),
                                TIMEOUT),
                PreparationSpawnPublishException.Code.SEND_FAILED);

        TestChannel missing = TestChannel.missingResult();
        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(delivery(alphaId, LobbyTeam.GREEN, 2)),
                                Map.of(alphaId, missing),
                                TIMEOUT),
                PreparationSpawnPublishException.Code.SEND_FAILED);
    }

    @Test
    void reportsOneBoundedTimeoutForAnIncompleteSend() {
        LobbyParticipantId alphaId = new LobbyParticipantId("alpha");
        TestChannel blocked = TestChannel.blocked();

        assertCode(
                () ->
                        PreparationSpawnPublisher.publish(
                                List.of(delivery(alphaId, LobbyTeam.GREEN, 2)),
                                Map.of(alphaId, blocked),
                                Duration.ofMillis(20)),
                PreparationSpawnPublishException.Code.SEND_TIMEOUT);
        assertThat(blocked.sent()).hasSize(1);
    }

    private static void assertAssignment(TestChannel channel, PreparationSpawnAssignment expected)
            throws PreparationProtocolException {
        List<SentMessage> sentMessages = channel.sent();
        assertThat(sentMessages).hasSize(1);
        SentMessage sent = sentMessages.getFirst();
        assertThat(sent.messageType()).isEqualTo(MessageType.PREPARATION_SPAWN_ASSIGNMENT);
        assertThat(PreparationSpawnProtocolCodec.decodeAssignment(sent.payload()))
                .isEqualTo(expected);
    }

    private static PreparationClientSpawn delivery(
            String participantId, LobbyTeam team, int spawnIndex) {
        return delivery(new LobbyParticipantId(participantId), team, spawnIndex);
    }

    private static PreparationClientSpawn delivery(
            LobbyParticipantId participantId, LobbyTeam team, int spawnIndex) {
        return new PreparationClientSpawn(
                participantId,
                new PreparationSpawnAssignment(
                        7L,
                        2L,
                        "arena-one",
                        digest(),
                        team,
                        spawnIndex,
                        spawnIndex * 10.0d,
                        2.0d,
                        0.0d,
                        90.0d));
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationSpawnAssignment.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }

    private static void assertCode(
            Runnable action, PreparationSpawnPublishException.Code expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        PreparationSpawnPublishException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private record SentMessage(MessageType messageType, byte[] payload) {
        private SentMessage {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    @FunctionalInterface
    private interface SendBehavior {
        CompletionStage<ReliableSendResult> send();
    }

    private static final class TestChannel implements ReliableChannel {
        private final SendBehavior behavior;
        private final List<SentMessage> sent = new ArrayList<>();

        private TestChannel(SendBehavior behavior) {
            this.behavior = behavior;
        }

        private static TestChannel completed() {
            return new TestChannel(
                    () -> CompletableFuture.completedFuture(new ReliableSendResult(1L)));
        }

        private static TestChannel failed() {
            return new TestChannel(
                    () -> CompletableFuture.failedFuture(new IllegalStateException("failed")));
        }

        private static TestChannel missingResult() {
            return new TestChannel(() -> CompletableFuture.completedFuture(null));
        }

        private static TestChannel blocked() {
            return new TestChannel(CompletableFuture::new);
        }

        private static TestChannel throwing() {
            return new TestChannel(
                    () -> {
                        throw new IllegalStateException("failed to start");
                    });
        }

        @Override
        public CompletionStage<ReliableSendResult> send(
                MessageType messageType, byte[] payload) {
            sent.add(new SentMessage(messageType, payload));
            return behavior.send();
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }

        private List<SentMessage> sent() {
            return List.copyOf(sent);
        }
    }
}
