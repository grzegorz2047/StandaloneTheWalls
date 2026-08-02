package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;

/** Application channel that fails closed if identity messages appear after authentication. */
final class PostIdentityReliableChannel implements ReliableChannel {
    private final ReliableChannel delegate;

    PostIdentityReliableChannel(ReliableChannel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
        Objects.requireNonNull(messageType, "messageType");
        if (isIdentityMessage(messageType)) {
            return failClosed(
                    new IdentityExchangeException(
                            IdentityExchangeException.Code.POST_AUTH_IDENTITY_MESSAGE,
                            "identity messages are forbidden after authentication"));
        }
        return delegate.send(messageType, payload);
    }

    @Override
    public CompletionStage<Optional<ProtocolEnvelope>> receive() {
        CompletableFuture<Optional<ProtocolEnvelope>> result = new CompletableFuture<>();
        delegate.receive()
                .whenComplete(
                        (message, failure) -> {
                            if (failure != null) {
                                result.completeExceptionally(unwrap(failure));
                                return;
                            }
                            if (message == null) {
                                closeThenComplete(
                                        new IdentityExchangeException(
                                                IdentityExchangeException.Code.INTERNAL_ERROR,
                                                "reliable channel returned a null receive result"),
                                        result);
                                return;
                            }
                            if (message.isPresent()
                                    && isIdentityMessage(message.orElseThrow().messageType())) {
                                IdentityExchangeException identityFailure =
                                        new IdentityExchangeException(
                                                IdentityExchangeException.Code
                                                        .POST_AUTH_IDENTITY_MESSAGE,
                                                "identity message received after authentication");
                                closeThenComplete(identityFailure, result);
                                return;
                            }
                            result.complete(message);
                        });
        return result.minimalCompletionStage();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public CompletionStage<Void> close() {
        return delegate.close();
    }

    private CompletionStage<ReliableSendResult> failClosed(IdentityExchangeException failure) {
        CompletableFuture<ReliableSendResult> result = new CompletableFuture<>();
        closeThenComplete(failure, result);
        return result.minimalCompletionStage();
    }

    private <T> void closeThenComplete(
            IdentityExchangeException failure, CompletableFuture<T> result) {
        delegate.close()
                .whenComplete(
                        (unused, closeFailure) -> {
                            if (closeFailure != null) {
                                failure.addSuppressed(unwrap(closeFailure));
                            }
                            result.completeExceptionally(failure);
                        });
    }

    private static boolean isIdentityMessage(MessageType messageType) {
        return messageType == MessageType.IDENTITY_CHALLENGE
                || messageType == MessageType.IDENTITY_PROOF
                || messageType == MessageType.IDENTITY_RESULT;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
