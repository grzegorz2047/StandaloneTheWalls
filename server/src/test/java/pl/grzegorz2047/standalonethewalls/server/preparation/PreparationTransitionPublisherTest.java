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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchResult;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchSnapshot;

class PreparationTransitionPublisherTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final MessageType[] COMPLETE_TRANSITION = {
        MessageType.LOBBY_MATCH_SNAPSHOT, MessageType.PREPARATION_SPAWN_ASSIGNMENT
    };

    @Test
    void publishesPreparationSnapshotBeforeEveryClientSpecificSpawn() throws Exception {
        LobbyRosterState roster = roster();
        LobbyMatchSnapshot matchSnapshot = preparation(roster);
        LobbyParticipantId alpha = new LobbyParticipantId("alpha");
        LobbyParticipantId bravo = new LobbyParticipantId("bravo");
        TestChannel alphaChannel = TestChannel.completed();
        TestChannel bravoChannel = TestChannel.completed();

        publish(roster, matchSnapshot, Map.of(alpha, alphaChannel, bravo, bravoChannel), TIMEOUT);

        assertOrderedDelivery(alphaChannel, TeamId.GREEN, 2, matchSnapshot);
        assertOrderedDelivery(bravoChannel, TeamId.BLUE, 8, matchSnapshot);
    }

    @Test
    void rejectsChannelMismatchBeforeStartingAnySend() {
        LobbyRosterState roster = roster();
        TestChannel channel = TestChannel.completed();
        Map<LobbyParticipantId, ReliableChannel> channels =
                Map.of(new LobbyParticipantId("alpha"), channel);

        assertCode(
                () -> publish(roster, preparation(roster), channels, TIMEOUT),
                PreparationTransitionPublishException.Code.CHANNEL_COVERAGE_MISMATCH);
        assertThat(channel.sent()).isEmpty();
    }

    @Test
    void snapshotFailurePreventsEverySpawnAssignment() {
        LobbyRosterState roster = roster();
        LobbyParticipantId alpha = new LobbyParticipantId("alpha");
        LobbyParticipantId bravo = new LobbyParticipantId("bravo");
        TestChannel alphaChannel = TestChannel.failedImmediately();
        TestChannel bravoChannel = TestChannel.completed();
        Map<LobbyParticipantId, ReliableChannel> channels =
                Map.of(alpha, alphaChannel, bravo, bravoChannel);

        assertCode(
                () -> publish(roster, preparation(roster), channels, TIMEOUT),
                PreparationTransitionPublishException.Code.SNAPSHOT_SEND_FAILED);
        assertMessageTypes(alphaChannel, MessageType.LOBBY_MATCH_SNAPSHOT);
        assertMessageTypes(bravoChannel, MessageType.LOBBY_MATCH_SNAPSHOT);
    }

    @Test
    void assignmentFailureIsTerminalAfterTheSnapshotBarrier() {
        LobbyRosterState roster = roster();
        LobbyParticipantId alpha = new LobbyParticipantId("alpha");
        LobbyParticipantId bravo = new LobbyParticipantId("bravo");
        TestChannel alphaChannel = TestChannel.failSecondSend();
        TestChannel bravoChannel = TestChannel.completed();
        Map<LobbyParticipantId, ReliableChannel> channels =
                Map.of(alpha, alphaChannel, bravo, bravoChannel);

        assertCode(
                () -> publish(roster, preparation(roster), channels, TIMEOUT),
                PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED);
        assertMessageTypes(alphaChannel, COMPLETE_TRANSITION);
        assertMessageTypes(bravoChannel, COMPLETE_TRANSITION);
    }

    @Test
    void oneDeadlineStopsTheTransitionBeforeAssignments() {
        LobbyRosterState roster = roster();
        LobbyParticipantId alpha = new LobbyParticipantId("alpha");
        LobbyParticipantId bravo = new LobbyParticipantId("bravo");
        TestChannel alphaChannel = TestChannel.blocked();
        TestChannel bravoChannel = TestChannel.completed();
        Map<LobbyParticipantId, ReliableChannel> channels =
                Map.of(alpha, alphaChannel, bravo, bravoChannel);

        assertCode(
                () -> publish(roster, preparation(roster), channels, Duration.ofMillis(20)),
                PreparationTransitionPublishException.Code.TIMEOUT);
        assertMessageTypes(alphaChannel, MessageType.LOBBY_MATCH_SNAPSHOT);
        assertMessageTypes(bravoChannel, MessageType.LOBBY_MATCH_SNAPSHOT);
    }

    private static void publish(
            LobbyRosterState roster,
            LobbyMatchSnapshot matchSnapshot,
            Map<LobbyParticipantId, ReliableChannel> channels,
            Duration timeout) {
        PreparationTransitionPublisher.publish(map(), roster, matchSnapshot, channels, timeout);
    }

    private static void assertMessageTypes(TestChannel channel, MessageType... expected) {
        assertThat(channel.sent()).extracting(SentMessage::messageType).containsExactly(expected);
    }

    private static void assertOrderedDelivery(
            TestChannel channel,
            TeamId expectedTeam,
            int expectedSpawnIndex,
            LobbyMatchSnapshot expectedMatch)
            throws Exception {
        List<SentMessage> sent = channel.sent();
        assertMessageTypes(channel, COMPLETE_TRANSITION);
        var match = LobbyMatchProtocolCodec.decodeSnapshot(sent.get(0).payload());
        assertThat(match.phase()).isEqualTo(LobbyMatchPhase.PREPARATION);
        assertThat(match.rosterRevision()).isEqualTo(expectedMatch.rosterRevision());
        assertThat(match.roundNumber()).isEqualTo(expectedMatch.roundNumber());
        var assignment = PreparationSpawnProtocolCodec.decodeAssignment(sent.get(1).payload());
        assertThat(assignment.team().name()).isEqualTo(expectedTeam.name());
        assertThat(assignment.spawnIndex()).isEqualTo(expectedSpawnIndex);
        assertThat(assignment.rosterRevision()).isEqualTo(expectedMatch.rosterRevision());
        assertThat(assignment.roundNumber()).isEqualTo(expectedMatch.roundNumber());
        assertThat(assignment.mapId()).isEqualTo("arena-one");
        assertThat(assignment.mapSha256()).containsExactly(digest());
    }

    private static LobbyRosterState roster() {
        return new LobbyRosterState(
                7L, List.of(participant("alpha", TeamId.GREEN), participant("bravo", TeamId.BLUE)));
    }

    private static LobbyParticipantState participant(String id, TeamId team) {
        return new LobbyParticipantState(new LobbyParticipantId(id), Optional.of(team), true);
    }

    private static LobbyMatchSnapshot preparation(LobbyRosterState roster) {
        return new LobbyMatchSnapshot(
                3L,
                roster.revision(),
                20L,
                MatchPhase.PREPARATION,
                100L,
                roster.participants().size(),
                2L,
                MatchResult.NONE,
                Optional.empty());
    }

    private static PreparationMapDefinition map() {
        PreparationSpawnPoint blue =
                new PreparationSpawnPoint(8, TeamId.BLUE, 80.0d, 2.0d, 0.0d, 90.0d);
        PreparationSpawnPoint green =
                new PreparationSpawnPoint(2, TeamId.GREEN, 20.0d, 2.0d, 0.0d, 0.0d);
        return new PreparationMapDefinition("arena-one", digest(), List.of(blue, green));
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationMapDefinition.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }

    private static void assertCode(
            Runnable action, PreparationTransitionPublishException.Code expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        PreparationTransitionPublishException.class,
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
        CompletionStage<ReliableSendResult> send(int invocation);
    }

    private static final class TestChannel implements ReliableChannel {
        private final SendBehavior behavior;
        private final AtomicInteger invocations = new AtomicInteger();
        private final List<SentMessage> sent = new ArrayList<>();

        private TestChannel(SendBehavior behavior) {
            this.behavior = behavior;
        }

        private static TestChannel completed() {
            return new TestChannel(
                    ignored -> CompletableFuture.completedFuture(new ReliableSendResult(1L)));
        }

        private static TestChannel failedImmediately() {
            return new TestChannel(
                    ignored -> CompletableFuture.failedFuture(new IllegalStateException("failed")));
        }

        private static TestChannel failSecondSend() {
            return new TestChannel(
                    invocation ->
                            invocation == 2
                                    ? CompletableFuture.failedFuture(
                                            new IllegalStateException("failed"))
                                    : CompletableFuture.completedFuture(
                                            new ReliableSendResult(1L)));
        }

        private static TestChannel blocked() {
            return new TestChannel(ignored -> new CompletableFuture<>());
        }

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            sent.add(new SentMessage(messageType, payload));
            return behavior.send(invocations.incrementAndGet());
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
