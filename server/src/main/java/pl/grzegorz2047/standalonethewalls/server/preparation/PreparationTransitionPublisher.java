package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchProtocolAdapter;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchSnapshot;

/** Publishes one complete ordered transition: phase snapshot first, then client-specific spawns. */
public final class PreparationTransitionPublisher {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofSeconds(30);

    private PreparationTransitionPublisher() {
        throw new AssertionError("No instances");
    }

    public static void publish(
            PreparationMapDefinition map,
            LobbyRosterState roster,
            LobbyMatchSnapshot matchSnapshot,
            Map<LobbyParticipantId, ReliableChannel> channels,
            Duration timeout) {
        Map<LobbyParticipantId, ReliableChannel> availableChannels =
                Map.copyOf(Objects.requireNonNull(channels, "channels"));
        Duration boundedTimeout = requireTimeout(timeout);
        List<PreparationClientSpawn> plan =
                PreparationTransitionPlanner.plan(map, roster, matchSnapshot);
        validateCoverageAndPayloads(plan, availableChannels);

        long deadline = System.nanoTime() + boundedTimeout.toNanos();
        byte[] snapshotPayload =
                LobbyMatchProtocolCodec.encodeSnapshot(
                        LobbyMatchProtocolAdapter.toProtocol(matchSnapshot));
        publishSnapshot(availableChannels, snapshotPayload, deadline);

        try {
            PreparationSpawnPublisher.publish(
                    plan, availableChannels, remainingDuration(deadline));
        } catch (PreparationSpawnPublishException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.ASSIGNMENT_PUBLISH_FAILED,
                    "preparation spawn assignment publication failed",
                    exception);
        }
    }

    private static void validateCoverageAndPayloads(
            List<PreparationClientSpawn> plan,
            Map<LobbyParticipantId, ReliableChannel> channels) {
        Set<LobbyParticipantId> participantIds = new HashSet<>();
        for (PreparationClientSpawn delivery : plan) {
            PreparationClientSpawn candidate = Objects.requireNonNull(delivery, "delivery");
            participantIds.add(candidate.participantId());
            PreparationSpawnProtocolCodec.encodeAssignment(candidate.assignment());
        }
        if (participantIds.size() != plan.size()
                || channels.size() != participantIds.size()
                || !channels.keySet().equals(participantIds)) {
            throw failure(
                    PreparationTransitionPublishException.Code.CHANNEL_COVERAGE_MISMATCH,
                    "preparation transition channels do not exactly cover the planned roster");
        }
    }

    private static void publishSnapshot(
            Map<LobbyParticipantId, ReliableChannel> channels, byte[] payload, long deadline) {
        List<CompletableFuture<ReliableSendResult>> sends = new ArrayList<>(channels.size());
        for (ReliableChannel channel : channels.values()) {
            sends.add(startSnapshotSend(channel, payload));
        }
        CompletableFuture<Void> all =
                CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new));
        try {
            all.get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.INTERRUPTED,
                    "interrupted while publishing the preparation phase snapshot",
                    exception);
        } catch (TimeoutException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.TIMEOUT,
                    "preparation phase snapshot publication timed out",
                    exception);
        } catch (ExecutionException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.SNAPSHOT_SEND_FAILED,
                    "preparation phase snapshot publication failed",
                    exception.getCause());
        }
    }

    private static CompletableFuture<ReliableSendResult> startSnapshotSend(
            ReliableChannel channel, byte[] payload) {
        CompletionStage<ReliableSendResult> stage;
        try {
            stage =
                    Objects.requireNonNull(
                            Objects.requireNonNull(channel, "channel")
                                    .send(MessageType.LOBBY_MATCH_SNAPSHOT, payload),
                            "preparation phase snapshot send stage");
        } catch (RuntimeException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.SNAPSHOT_SEND_START_FAILED,
                    "preparation phase snapshot send could not be started",
                    exception);
        }
        CompletableFuture<ReliableSendResult> bridged = new CompletableFuture<>();
        try {
            stage.whenComplete(
                    (result, sendFailure) -> {
                        if (sendFailure != null) {
                            bridged.completeExceptionally(sendFailure);
                        } else if (result == null) {
                            bridged.completeExceptionally(
                                    new IllegalStateException(
                                            "preparation phase snapshot send returned no result"));
                        } else {
                            bridged.complete(result);
                        }
                    });
        } catch (RuntimeException exception) {
            throw new PreparationTransitionPublishException(
                    PreparationTransitionPublishException.Code.SNAPSHOT_SEND_START_FAILED,
                    "preparation phase snapshot completion could not be observed",
                    exception);
        }
        return bridged;
    }

    private static Duration remainingDuration(long deadline) {
        return Duration.ofNanos(remainingNanos(deadline));
    }

    private static long remainingNanos(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            throw failure(
                    PreparationTransitionPublishException.Code.TIMEOUT,
                    "preparation transition exceeded its bounded timeout");
        }
        return remaining;
    }

    private static Duration requireTimeout(Duration timeout) {
        Duration candidate = Objects.requireNonNull(timeout, "timeout");
        if (candidate.isZero()
                || candidate.isNegative()
                || candidate.compareTo(MAXIMUM_TIMEOUT) > 0
                || candidate.toMillis() < 1L) {
            throw new IllegalArgumentException(
                    "preparation transition timeout is outside the supported range");
        }
        return candidate;
    }

    private static PreparationTransitionPublishException failure(
            PreparationTransitionPublishException.Code code, String message) {
        return new PreparationTransitionPublishException(code, message);
    }
}
