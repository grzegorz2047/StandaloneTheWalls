package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;

class AsyncTlsReliableChannelCancellationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void cancellingACallerFutureViewDoesNotCancelTheAdmittedWrite()
            throws InterruptedException, ExecutionException, TimeoutException {
        BlockingSendStream stream = new BlockingSendStream();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AsyncTlsReliableChannel channel =
                new AsyncTlsReliableChannel(
                        stream,
                        new AsyncReliableChannelConfig(2, 16L, Duration.ofSeconds(1)),
                        executor,
                        Thread.ofPlatform().name("test-cancellation-close").factory());

        CompletionStage<ReliableSendResult> sent =
                channel.send(MessageType.PING, new byte[] {7});
        stream.awaitStarted();
        CompletableFuture<ReliableSendResult> callerView = sent.toCompletableFuture();
        assertThat(callerView.cancel(true)).isTrue();

        stream.release();
        stream.awaitCompleted();
        assertThat(stream.payload()).containsExactly(7);
        await(channel.close());
        assertThat(executor.isTerminated()).isTrue();
    }

    private static <T> T await(CompletionStage<T> stage)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static final class BlockingSendStream implements ReliableEnvelopeStream {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean open = true;
        private volatile byte[] payload;

        private void awaitStarted() throws InterruptedException {
            await(started, "send start");
        }

        private void release() {
            release.countDown();
        }

        private void awaitCompleted() throws InterruptedException {
            await(completed, "send completion");
        }

        private byte[] payload() {
            return payload.clone();
        }

        @Override
        public long send(MessageType messageType, byte[] sentPayload) throws IOException {
            started.countDown();
            try {
                if (!release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("send release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("send was interrupted", exception);
            }
            if (!open) {
                throw new IOException("stream is closed");
            }
            payload = sentPayload.clone();
            completed.countDown();
            return 0L;
        }

        @Override
        public Optional<ProtocolEnvelope> receive() throws ProtocolException {
            return Optional.empty();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            release.countDown();
        }

        private static void await(CountDownLatch latch, String operation)
                throws InterruptedException {
            if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError(operation + " timed out");
            }
        }
    }
}
