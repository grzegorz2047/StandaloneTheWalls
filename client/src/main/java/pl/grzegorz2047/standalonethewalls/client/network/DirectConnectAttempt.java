package pl.grzegorz2047.standalonethewalls.client.network;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** One cancellable attempt. Callers cannot complete the returned result stage. */
public final class DirectConnectAttempt {
    private final CompletableFuture<DirectConnectResult> result;
    private final BooleanSupplier cancellation;

    DirectConnectAttempt(
            CompletableFuture<DirectConnectResult> result, BooleanSupplier cancellation) {
        this.result = Objects.requireNonNull(result, "result");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    public CompletionStage<DirectConnectResult> result() {
        return result.minimalCompletionStage();
    }

    public boolean cancel() {
        return cancellation.getAsBoolean();
    }

    public boolean isDone() {
        return result.isDone();
    }

    static DirectConnectAttempt completed(DirectConnectFailureCode code) {
        return new DirectConnectAttempt(
                CompletableFuture.completedFuture(
                        new DirectConnectResult.Failed(DirectConnectFailure.of(code))),
                () -> false);
    }
}
