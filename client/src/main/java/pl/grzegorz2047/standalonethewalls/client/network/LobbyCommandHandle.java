package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Correlation handle for one accepted local lobby command submission. */
public record LobbyCommandHandle(
        long requestId, CompletionStage<LobbyCommandResolution> completion) {
    public LobbyCommandHandle {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        completion =
                Objects.requireNonNull(completion, "completion")
                        .toCompletableFuture()
                        .minimalCompletionStage();
    }
}
