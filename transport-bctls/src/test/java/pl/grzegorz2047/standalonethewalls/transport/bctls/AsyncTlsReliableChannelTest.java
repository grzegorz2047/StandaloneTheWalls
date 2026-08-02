package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannelException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;

class AsyncTlsReliableChannelTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ThreadFactory CLOSER_FACTORY =
            Thread.ofPlatform().name("test-reliable-close").factory();

    @Test
    void runsSendOffCallerDefensivelyCopiesPayloadAndTerminatesItsExecutor()
            throws InterruptedException, ExecutionException, TimeoutException {
        TestEnvelopeStream stream = new TestEnvelopeStream();
        stream.blockSend();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncTlsReliableChannel channel = channel(stream, executor, 4, 16L);
        Thread caller = Thread.currentThread();
        byte[] payload = {1};

        CompletionStage<ReliableSendResult> sent = channel.send(MessageType.PING, payload);
        stream.awaitSendStarted();
        payload[0] = 9;
        stream.releaseSend();

        assertThat(await(sent).sequence()).isZero();
        assertThat(stream.sendThread()).isNotSameAs(caller);
        assertThat(stream.capturedPayload()).containsExactly(1);
        await(channel.close());
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void enforcesPendingOperationAndByteLimitsWithoutClosingTheChannel()
            throws InterruptedException, ExecutionException, TimeoutException {
        TestEnvelopeStream operationStream = new TestEnvelopeStream();
        operationStream.blockSend();
        ExecutorService operationExecutor = Executors.newSingleThreadExecutor();
        AsyncTlsReliableChannel operationChannel =
                channel(operationStream, operationExecutor, 1, 16L);

        CompletionStage<ReliableSendResult> first =
                operationChannel.send(MessageType.PING, new byte[] {1});
        operationStream.awaitSendStarted();
        assertChannelFailure(
                operationChannel.send(MessageType.PING, new byte[] {2}),
                ReliableChannelException.Code.SEND_LIMIT_EXCEEDED);
        assertThat(operationChannel.isOpen()).isTrue();
        operationStream.releaseSend();
        assertThat(await(first).sequence()).isZero();
        await(operationChannel.close());

        TestEnvelopeStream byteStream = new TestEnvelopeStream();
        ExecutorService byteExecutor = Executors.newSingleThreadExecutor();
        AsyncTlsReliableChannel byteChannel = channel(byteStream, byteExecutor, 2, 1L);
        assertChannelFailure(
                byteChannel.send(MessageType.PING, new byte[] {1, 2}),
                ReliableChannelException.Code.SEND_LIMIT_EXCEEDED);
        assertThat(byteChannel.isOpen()).isTrue();
        await(byteChannel.close());
    }

    @Test
    void rejectsASecondReceiveAndCloseCompletesTheBlockedReceive()
            throws InterruptedException, ExecutionException, TimeoutException {
        TestEnvelopeStream stream = new TestEnvelopeStream();
        stream.blockReceive();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncTlsReliableChannel channel = channel(stream, executor, 4, 16L);

        CompletionStage<Optional<ProtocolEnvelope>> first = channel.receive();
        stream.awaitReceiveStarted();
        assertChannelFailure(
                channel.receive(), ReliableChannelException.Code.RECEIVE_IN_PROGRESS);

        CompletionStage<Void> closed = channel.close();
        assertChannelFailure(first, ReliableChannelException.Code.CLOSED);
        await(closed);
        assertThat(channel.isOpen()).isFalse();
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void preservesATerminalFailureForCloseAndLaterOperations()
            throws InterruptedException, TimeoutException {
        IOException terminal = new IOException("terminal receive failure");
        TestEnvelopeStream stream = new TestEnvelopeStream();
        stream.failReceiveWith(terminal);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncTlsReliableChannel channel = channel(stream, executor, 4, 16L);

        assertThat(failure(channel.receive())).isSameAs(terminal);
        assertThat(failure(channel.close())).isSameAs(terminal);
        ReliableChannelException rejected =
                assertChannelFailure(
                        channel.send(MessageType.PING, new byte[] {1}),
                        ReliableChannelException.Code.FAILED);
        assertThat(rejected).hasCause(terminal);
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void cleanEofClosesTheChannelSuccessfully()
            throws InterruptedException, ExecutionException, TimeoutException {
        TestEnvelopeStream stream = new TestEnvelopeStream();
        stream.endReceiveCleanly();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncTlsReliableChannel channel = channel(stream, executor, 4, 16L);

        assertThat(await(channel.receive())).isEmpty();
        await(channel.close());
        assertThat(channel.isOpen()).isFalse();
        assertThat(executor.isTerminated()).isTrue();
    }

    private static AsyncTlsReliableChannel channel(
            TestEnvelopeStream stream,
            ExecutorService executor,
            int maximumPendingSends,
            long maximumPendingSendBytes) {
        return new AsyncTlsReliableChannel(
                stream,
                new AsyncReliableChannelConfig(
                        maximumPendingSends, maximumPendingSendBytes, Duration.ofSeconds(1)),
                executor,
                CLOSER_FACTORY);
    }

    private static ReliableChannelException assertChannelFailure(
            CompletionStage<?> stage, ReliableChannelException.Code expectedCode)
            throws InterruptedException, TimeoutException {
        Throwable failure = failure(stage);
        assertThat(failure).isInstanceOf(ReliableChannelException.class);
        ReliableChannelException channelFailure = (ReliableChannelException) failure;
        assertThat(channelFailure.code()).isEqualTo(expectedCode);
        return channelFailure;
    }

    private static Throwable failure(CompletionStage<?> stage)
            throws InterruptedException, TimeoutException {
        try {
            await(stage);
            throw new AssertionError("expected the stage to fail");
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    private static <T> T await(CompletionStage<T> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static final class TestEnvelopeStream implements ReliableEnvelopeStream {
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicLong sequence = new AtomicLong();
        private final CountDownLatch sendStarted = new CountDownLatch(1);
        private final CountDownLatch sendRelease = new CountDownLatch(1);
        private final CountDownLatch receiveStarted = new CountDownLatch(1);
        private final CountDownLatch receiveRelease = new CountDownLatch(1);

        private volatile boolean sendBlocked;
        private volatile boolean receiveBlocked;
        private volatile Optional<ProtocolEnvelope> receiveResult = Optional.empty();
        private volatile IOException receiveFailure;
        private volatile Thread sendThread;
        private volatile byte[] capturedPayload;

        private void blockSend() {
            sendBlocked = true;
        }

        private void releaseSend() {
            sendRelease.countDown();
        }

        private void blockReceive() {
            receiveBlocked = true;
        }

        private void failReceiveWith(IOException failure) {
            receiveFailure = failure;
        }

        private void endReceiveCleanly() {
            receiveResult = Optional.empty();
        }

        private void awaitSendStarted() throws InterruptedException {
            if (!sendStarted.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("send did not start");
            }
        }

        private void awaitReceiveStarted() throws InterruptedException {
            if (!receiveStarted.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("receive did not start");
            }
        }

        private Thread sendThread() {
            return sendThread;
        }

        private byte[] capturedPayload() {
            return capturedPayload.clone();
        }

        @Override
        public long send(MessageType messageType, byte[] payload) throws IOException {
            sendThread = Thread.currentThread();
            sendStarted.countDown();
            if (sendBlocked) {
                await(sendRelease, "send release");
            }
            ensureOpen();
            capturedPayload = payload.clone();
            return sequence.getAndIncrement();
        }

        @Override
        public Optional<ProtocolEnvelope> receive() throws IOException, ProtocolException {
            receiveStarted.countDown();
            if (receiveBlocked) {
                await(receiveRelease, "receive release");
            }
            ensureOpen();
            if (receiveFailure != null) {
                throw receiveFailure;
            }
            return receiveResult;
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void close() {
            open.set(false);
            sendRelease.countDown();
            receiveRelease.countDown();
        }

        private void ensureOpen() throws IOException {
            if (!open.get()) {
                throw new IOException("test stream is closed");
            }
        }

        private static void await(CountDownLatch latch, String name) throws IOException {
            try {
                if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException(name + " timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while awaiting " + name, exception);
            }
        }
    }
}
