package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketResult;

/** Correlation handle for one accepted realtime ticket request. */
public record RealtimeTicketHandle(
        long requestId, CompletionStage<RealtimeTicketResult> completion) {
    public RealtimeTicketHandle {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        completion =
                Objects.requireNonNull(completion, "completion")
                        .toCompletableFuture()
                        .minimalCompletionStage();
    }
}
