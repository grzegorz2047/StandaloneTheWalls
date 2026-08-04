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
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;

/** Validates and publishes one complete participant-targeted preparation spawn plan. */
public final class PreparationSpawnPublisher {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofSeconds(30);

    private PreparationSpawnPublisher() {
        throw new AssertionError("No instances");
    }

    public static void publish(
            List<PreparationClientSpawn> plan,
            Map<LobbyParticipantId, ReliableChannel> channels,
            Duration timeout) {
        List<PreparationClientSpawn> deliveries =
                List.copyOf(Objects.requireNonNull(plan, "plan"));
        Map<LobbyParticipantId, ReliableChannel> availableChannels =
                Objects.requireNonNull(channels, "channels");
        Duration boundedTimeout = requireTimeout(timeout);
        if (deliveries.isEmpty()) {
            throw failure(
                    PreparationSpawnPublishException.Code.EMPTY_PLAN,
                    "preparation spawn publish plan cannot be empty");
        }

        Set<LobbyParticipantId> participantIds = new HashSet<>();
        List<PendingDelivery> pending = new ArrayList<>(deliveries.size());
        for (PreparationClientSpawn delivery : deliveries) {
            PreparationClientSpawn candidate = Objects.requireNonNull(delivery, "delivery");
            if (!participantIds.add(candidate.participantId())) {
                throw failure(
                        PreparationSpawnPublishException.Code.DUPLICATE_PARTICIPANT,
                        "preparation spawn publish plan contains a duplicate participant");
            }
            ReliableChannel channel = availableChannels.get(candidate.participantId());
            if (channel == null) {
                throw failure(
                        PreparationSpawnPublishException.Code.CHANNEL_COVERAGE_MISMATCH,
                        "preparation spawn publish plan has no channel for one participant");
            }
            pending.add(
                    new PendingDelivery(
                            channel,
                            PreparationSpawnProtocolCodec.encodeAssignment(
                                    candidate.assignment())));
        }
        if (availableChannels.size() != participantIds.size()
                || !availableChannels.keySet().equals(participantIds)) {
            throw failure(
                    PreparationSpawnPublishException.Code.CHANNEL_COVERAGE_MISMATCH,
                    "preparation spawn channels do not exactly cover the publish plan");
        }

        List<CompletableFuture<ReliableSendResult>> sends = new ArrayList<>(pending.size());
        for (PendingDelivery delivery : pending) {
            sends.add(startSend(delivery));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new));
        try {
            all.get(boundedTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PreparationSpawnPublishException(
                    PreparationSpawnPublishException.Code.INTERRUPTED,
                    "interrupted while publishing preparation spawn assignments",
                    exception);
        } catch (TimeoutException exception) {
            throw new PreparationSpawnPublishException(
                    PreparationSpawnPublishException.Code.SEND_TIMEOUT,
                    "preparation spawn assignment publish timed out",
                    exception);
        } catch (ExecutionException exception) {
            throw new PreparationSpawnPublishException(
                    PreparationSpawnPublishException.Code.SEND_FAILED,
                    "preparation spawn assignment publish failed",
                    exception.getCause());
        }
    }

    private static CompletableFuture<ReliableSendResult> startSend(PendingDelivery delivery) {
        CompletionStage<ReliableSendResult> stage;
        try {
            stage =
                    Objects.requireNonNull(
                            delivery.channel().send(
                                    MessageType.PREPARATION_SPAWN_ASSIGNMENT,
                                    delivery.payload()),
                            "preparation spawn send stage");
        } catch (RuntimeException exception) {
            throw new PreparationSpawnPublishException(
                    PreparationSpawnPublishException.Code.SEND_START_FAILED,
                    "preparation spawn assignment send could not be started",
                    exception);
        }

        CompletableFuture<ReliableSendResult> bridged = new CompletableFuture<>();
        try {
            stage.whenComplete(
                    (result, failure) -> {
                        if (failure != null) {
                            bridged.completeExceptionally(failure);
                        } else if (result == null) {
                            bridged.completeExceptionally(
                                    new IllegalStateException(
                                            "preparation spawn send returned no result"));
                        } else {
                            bridged.complete(result);
                        }
                    });
        } catch (RuntimeException exception) {
            throw new PreparationSpawnPublishException(
                    PreparationSpawnPublishException.Code.SEND_START_FAILED,
                    "preparation spawn assignment completion could not be observed",
                    exception);
        }
        return bridged;
    }

    private static Duration requireTimeout(Duration timeout) {
        Duration candidate = Objects.requireNonNull(timeout, "timeout");
        if (candidate.isZero()
                || candidate.isNegative()
                || candidate.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "preparation spawn publish timeout is outside the supported range");
        }
        return candidate;
    }

    private static PreparationSpawnPublishException failure(
            PreparationSpawnPublishException.Code code, String message) {
        return new PreparationSpawnPublishException(code, message);
    }

    private record PendingDelivery(ReliableChannel channel, byte[] payload) {
        private PendingDelivery {
            Objects.requireNonNull(channel, "channel");
            payload = Objects.requireNonNull(payload, "payload").clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
