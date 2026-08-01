package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.concurrent.CompletionStage;

/** Low-latency transport boundary; reliability semantics belong to message design. */
public interface RealtimeChannel {
    CompletionStage<Void> send(ProtocolEnvelope envelope);

    /** Completes with the next validated envelope delivered by this channel. */
    CompletionStage<ProtocolEnvelope> receive();

    boolean isOpen();

    void close();
}
