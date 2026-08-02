package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Ordered reliable transport boundary without a concrete socket dependency.
 *
 * <p>The transport owns the session identifier and outbound sequence. Implementations must not run
 * blocking I/O on the calling thread.
 */
public interface ReliableChannel {
    CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload);

    /** Completes with an empty result only after a clean peer end-of-stream. */
    CompletionStage<Optional<ProtocolEnvelope>> receive();

    boolean isOpen();

    /** Starts an idempotent asynchronous close and completes when owned resources terminate. */
    CompletionStage<Void> close();
}
