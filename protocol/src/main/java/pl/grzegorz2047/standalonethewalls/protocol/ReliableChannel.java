package pl.grzegorz2047.standalonethewalls.protocol;

import java.util.concurrent.CompletionStage;

/** Ordered, reliable transport boundary without a concrete socket dependency. */
public interface ReliableChannel {
    CompletionStage<Void> send(ProtocolEnvelope envelope);

    /** Completes with the next validated envelope delivered by this channel. */
    CompletionStage<ProtocolEnvelope> receive();

    boolean isOpen();

    void close();
}
