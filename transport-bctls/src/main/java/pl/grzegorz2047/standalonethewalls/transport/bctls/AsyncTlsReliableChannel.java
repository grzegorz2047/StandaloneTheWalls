package pl.grzegorz2047.standalonethewalls.transport.bctls;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannelException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;

/**
 * Asynchronous reliable channel backed by one blocking authenticated TLS envelope stream.
 *
 * <p>The channel owns a named virtual-thread-per-task executor and a separate virtual closer. It
 * never uses a common pool and never performs blocking stream I/O on the API caller thread.
 */
public final class AsyncTlsReliableChannel implements ReliableChannel {
    private static final AtomicLong CHANNEL_IDS = new AtomicLong();

    private final ReliableEnvelopeStream stream;
    private final AsyncReliableChannelConfig config;
    private final ExecutorService ioExecutor;
    private final ThreadFactory closeThreadFactory;
    private final Object stateLock = new Object();
    private final Set<SendOperation> pendingSends =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    private volatile State state = State.OPEN;
    private long pendingSendBytes;
    private CompletableFuture<Optional<ProtocolEnvelope>> activeReceive;
    private Throwable terminalFailure;

    public AsyncTlsReliableChannel(TlsEnvelopeStream stream) {
        this(stream, AsyncReliableChannelConfig.DEFAULT);
    }

    public AsyncTlsReliableChannel(TlsEnvelopeStream stream, AsyncReliableChannelConfig config) {
        this(stream, config, createOwnedResources());
    }

    private AsyncTlsReliableChannel(
            ReliableEnvelopeStream stream,
            AsyncReliableChannelConfig config,
            OwnedResources resources) {
        this(stream, config, resources.ioExecutor(), resources.closeThreadFactory());
    }

    AsyncTlsReliableChannel(
            ReliableEnvelopeStream stream,
            AsyncReliableChannelConfig config,
            ExecutorService ownedIoExecutor,
            ThreadFactory ownedCloseThreadFactory) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.config = Objects.requireNonNull(config, "config");
        this.ioExecutor = Objects.requireNonNull(ownedIoExecutor, "ownedIoExecutor");
        this.closeThreadFactory =
                Objects.requireNonNull(ownedCloseThreadFactory, "ownedCloseThreadFactory");
    }

    @Override
    public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
        Objects.requireNonNull(messageType, "messageType");
        byte[] source = Objects.requireNonNull(payload, "payload");
        Throwable validationFailure = validateSend(messageType, source.length);
        if (validationFailure != null) {
            return failedStage(validationFailure);
        }

        byte[] payloadCopy = source.clone();
        SendOperation operation;
        synchronized (stateLock) {
            Throwable unavailable = unavailableFailureLocked();
            if (unavailable != null) {
                return failedStage(unavailable);
            }
            if (pendingSends.size() >= config.maximumPendingSends()
                    || payloadCopy.length > config.maximumPendingSendBytes() - pendingSendBytes) {
                return failedStage(
                        new ReliableChannelException(
                                ReliableChannelException.Code.SEND_LIMIT_EXCEEDED,
                                "the reliable send admission limit is exhausted"));
            }
            operation = new SendOperation(messageType, payloadCopy);
            pendingSends.add(operation);
            pendingSendBytes += payloadCopy.length;
        }

        try {
            ioExecutor.execute(operation);
        } catch (RejectedExecutionException exception) {
            ReliableChannelException failure =
                    new ReliableChannelException(
                            ReliableChannelException.Code.EXECUTOR_REJECTED,
                            "the reliable channel I/O executor rejected a send",
                            exception);
            failIfOpen(failure);
            operation.fail(failure);
        }
        return operation.stage();
    }

    @Override
    public CompletionStage<Optional<ProtocolEnvelope>> receive() {
        CompletableFuture<Optional<ProtocolEnvelope>> result;
        synchronized (stateLock) {
            Throwable unavailable = unavailableFailureLocked();
            if (unavailable != null) {
                return failedStage(unavailable);
            }
            if (activeReceive != null) {
                return failedStage(
                        new ReliableChannelException(
                                ReliableChannelException.Code.RECEIVE_IN_PROGRESS,
                                "one reliable receive is already in progress"));
            }
            result = new CompletableFuture<>();
            activeReceive = result;
        }

        try {
            ioExecutor.execute(() -> runReceive(result));
        } catch (RejectedExecutionException exception) {
            clearActiveReceive(result);
            ReliableChannelException failure =
                    new ReliableChannelException(
                            ReliableChannelException.Code.EXECUTOR_REJECTED,
                            "the reliable channel I/O executor rejected a receive",
                            exception);
            failIfOpen(failure);
            result.completeExceptionally(failure);
        }
        return result.minimalCompletionStage();
    }

    @Override
    public boolean isOpen() {
        return state == State.OPEN && stream.isOpen();
    }

    @Override
    public CompletionStage<Void> close() {
        return initiateTermination(null, "the reliable channel is closing");
    }

    private void runReceive(CompletableFuture<Optional<ProtocolEnvelope>> result) {
        if (result.isDone()) {
            clearActiveReceive(result);
            return;
        }
        try {
            Optional<ProtocolEnvelope> received = stream.receive();
            clearActiveReceive(result);
            if (received.isEmpty()) {
                initiateTermination(null, "the peer ended the reliable stream");
            }
            result.complete(received);
        } catch (IOException | ProtocolException | RuntimeException exception) {
            clearActiveReceive(result);
            failIfOpen(exception);
            result.completeExceptionally(exception);
        }
    }

    private CompletionStage<Void> initiateTermination(Throwable failure, String pendingMessage) {
        List<SendOperation> sends;
        CompletableFuture<Optional<ProtocolEnvelope>> receive;
        Throwable operationFailure;
        synchronized (stateLock) {
            if (state != State.OPEN) {
                return closeFuture.minimalCompletionStage();
            }
            state = State.CLOSING;
            terminalFailure = failure;
            sends = List.copyOf(pendingSends);
            receive = activeReceive;
            activeReceive = null;
            operationFailure =
                    failure != null
                            ? failure
                            : new ReliableChannelException(
                                    ReliableChannelException.Code.CLOSED, pendingMessage);
        }

        for (SendOperation send : sends) {
            send.fail(operationFailure);
        }
        if (receive != null) {
            receive.completeExceptionally(operationFailure);
        }

        Thread closeThread = closeThreadFactory.newThread(this::runCloser);
        closeThread.start();
        return closeFuture.minimalCompletionStage();
    }

    private void runCloser() {
        Throwable closeFailure = null;
        try {
            stream.close();
        } catch (IOException | RuntimeException exception) {
            closeFailure = exception;
        }

        ioExecutor.shutdown();
        try {
            long timeoutNanos = config.closeTimeout().toNanos();
            if (!ioExecutor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) {
                ioExecutor.shutdownNow();
                if (!ioExecutor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) {
                    closeFailure =
                            combine(
                                    closeFailure,
                                    new ReliableChannelException(
                                            ReliableChannelException.Code.CLOSE_TIMEOUT,
                                            "the reliable channel I/O executor did not terminate"));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
            closeFailure =
                    combine(
                            closeFailure,
                            new ReliableChannelException(
                                    ReliableChannelException.Code.FAILED,
                                    "interrupted while terminating reliable channel I/O",
                                    exception));
        }

        Throwable finalFailure;
        synchronized (stateLock) {
            terminalFailure = combine(terminalFailure, closeFailure);
            finalFailure = terminalFailure;
            state = finalFailure == null ? State.CLOSED : State.FAILED;
        }
        if (finalFailure == null) {
            closeFuture.complete(null);
        } else {
            closeFuture.completeExceptionally(finalFailure);
        }
    }

    private void failIfOpen(Throwable failure) {
        if (state == State.OPEN) {
            initiateTermination(failure, "the reliable channel failed");
        }
    }

    private void clearActiveReceive(CompletableFuture<Optional<ProtocolEnvelope>> result) {
        synchronized (stateLock) {
            if (activeReceive == result) {
                activeReceive = null;
            }
        }
    }

    private void release(SendOperation operation) {
        if (!operation.released.compareAndSet(false, true)) {
            return;
        }
        synchronized (stateLock) {
            if (pendingSends.remove(operation)) {
                pendingSendBytes -= operation.payload.length;
            }
        }
    }

    private Throwable unavailableFailureLocked() {
        if (state == State.OPEN) {
            return null;
        }
        if (terminalFailure != null) {
            return new ReliableChannelException(
                    ReliableChannelException.Code.FAILED,
                    "the reliable channel has failed",
                    terminalFailure);
        }
        return new ReliableChannelException(
                ReliableChannelException.Code.CLOSED, "the reliable channel is closed");
    }

    private Throwable validateSend(MessageType messageType, int payloadBytes) {
        try {
            ReliableMessagePolicy.requireAllowed(messageType);
        } catch (ProtocolException exception) {
            return exception;
        }
        if (payloadBytes > ProtocolCodec.MAXIMUM_PAYLOAD_BYTES
                || payloadBytes > messageType.maximumPayloadBytes()) {
            return new ProtocolException(
                    ProtocolException.Code.INVALID_LENGTH,
                    "payload length is outside the allowed range");
        }
        if (payloadBytes > config.maximumPendingSendBytes()) {
            return new ReliableChannelException(
                    ReliableChannelException.Code.SEND_LIMIT_EXCEEDED,
                    "one payload exceeds the reliable send byte limit");
        }
        return null;
    }

    private static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary != null && secondary != primary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    private static <T> CompletionStage<T> failedStage(Throwable failure) {
        return CompletableFuture.<T>failedFuture(failure).minimalCompletionStage();
    }

    private static OwnedResources createOwnedResources() {
        long channelId = CHANNEL_IDS.incrementAndGet();
        String prefix = "sunderfront-reliable-" + channelId;
        ExecutorService executor =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name(prefix + "-io-", 0L).factory());
        ThreadFactory closer = Thread.ofVirtual().name(prefix + "-close").factory();
        return new OwnedResources(executor, closer);
    }

    private final class SendOperation implements Runnable {
        private final MessageType messageType;
        private final byte[] payload;
        private final CompletableFuture<ReliableSendResult> result = new CompletableFuture<>();
        private final AtomicBoolean released = new AtomicBoolean();

        private SendOperation(MessageType messageType, byte[] payload) {
            this.messageType = messageType;
            this.payload = payload;
        }

        private CompletionStage<ReliableSendResult> stage() {
            return result.minimalCompletionStage();
        }

        private void fail(Throwable failure) {
            result.completeExceptionally(failure);
            release(this);
        }

        @Override
        public void run() {
            if (result.isDone()) {
                release(this);
                return;
            }
            try {
                long sequence = stream.send(messageType, payload);
                result.complete(new ReliableSendResult(sequence));
            } catch (IOException | ProtocolException | RuntimeException exception) {
                failIfOpen(exception);
                result.completeExceptionally(exception);
            } finally {
                release(this);
            }
        }
    }

    private record OwnedResources(ExecutorService ioExecutor, ThreadFactory closeThreadFactory) {}

    private enum State {
        OPEN,
        CLOSING,
        CLOSED,
        FAILED
    }
}
