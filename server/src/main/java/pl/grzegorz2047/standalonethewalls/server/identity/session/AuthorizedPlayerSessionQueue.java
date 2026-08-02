package pl.grzegorz2047.standalonethewalls.server.identity.session;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded non-blocking handoff queue between transport admission and the future lobby runtime.
 *
 * <p>The queue owns committed sessions until {@link #poll()} or {@link #drain(int)} transfers
 * ownership to the caller. Closing the queue closes every session that has not been transferred.
 */
public final class AuthorizedPlayerSessionQueue implements AutoCloseable {
    public static final int MAXIMUM_CAPACITY = 10_000;
    private static final Duration MAXIMUM_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private final int capacity;
    private final Duration closeTimeout;
    private final ArrayDeque<AuthorizedPlayerSession> sessions = new ArrayDeque<>();
    private int reservedSlots;
    private boolean closed;

    public AuthorizedPlayerSessionQueue(int capacity, Duration closeTimeout) {
        if (capacity < 1 || capacity > MAXIMUM_CAPACITY) {
            throw new IllegalArgumentException("capacity is outside the safe range");
        }
        Duration timeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAXIMUM_CLOSE_TIMEOUT) > 0
                || timeout.toMillis() < 1L) {
            throw new IllegalArgumentException("closeTimeout is outside the safe range");
        }
        this.capacity = capacity;
        this.closeTimeout = timeout;
    }

    public synchronized Optional<AuthorizedPlayerSession> poll() {
        return Optional.ofNullable(sessions.pollFirst());
    }

    public synchronized List<AuthorizedPlayerSession> drain(int maximumSessions) {
        if (maximumSessions < 1 || maximumSessions > capacity) {
            throw new IllegalArgumentException("maximumSessions is outside the safe range");
        }
        List<AuthorizedPlayerSession> drained =
                new ArrayList<>(Math.min(maximumSessions, sessions.size()));
        while (drained.size() < maximumSessions) {
            AuthorizedPlayerSession session = sessions.pollFirst();
            if (session == null) {
                break;
            }
            drained.add(session);
        }
        return List.copyOf(drained);
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized int reservedSlotCount() {
        return reservedSlots;
    }

    public synchronized int capacity() {
        return capacity;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    synchronized Optional<Reservation> tryReserve() {
        if (closed || sessions.size() + reservedSlots >= capacity) {
            return Optional.empty();
        }
        reservedSlots++;
        return Optional.of(new Reservation(this));
    }

    @Override
    public void close() {
        List<AuthorizedPlayerSession> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(sessions);
            sessions.clear();
        }
        closeSessions(pending);
    }

    private synchronized boolean commit(Reservation reservation, AuthorizedPlayerSession session) {
        if (!reservation.claim()) {
            return false;
        }
        reservedSlots--;
        if (closed) {
            return false;
        }
        sessions.addLast(Objects.requireNonNull(session, "session"));
        return true;
    }

    private synchronized void cancel(Reservation reservation) {
        if (reservation.claim()) {
            reservedSlots--;
        }
    }

    private void closeSessions(List<AuthorizedPlayerSession> pending) {
        if (pending.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] closures =
                pending.stream()
                        .map(AuthorizedPlayerSession::closeAsync)
                        .map(CompletionStageSupport::toCompletableFuture)
                        .toArray(CompletableFuture<?>[]::new);
        try {
            CompletableFuture.allOf(closures).get(closeTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while closing queued player sessions", exception);
        } catch (ExecutionException | CompletionException exception) {
            throw new IllegalStateException(
                    "queued player session close failed", unwrap(exception));
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "queued player sessions did not close within the bounded timeout", exception);
        }
    }

    static final class Reservation implements AutoCloseable {
        private final AuthorizedPlayerSessionQueue queue;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Reservation(AuthorizedPlayerSessionQueue queue) {
            this.queue = queue;
        }

        boolean commit(AuthorizedPlayerSession session) {
            return queue.commit(this, session);
        }

        @Override
        public void close() {
            queue.cancel(this);
        }

        private boolean claim() {
            return active.compareAndSet(true, false);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class CompletionStageSupport {
        private CompletionStageSupport() {
            throw new AssertionError("No instances");
        }

        private static CompletableFuture<Void> toCompletableFuture(
                java.util.concurrent.CompletionStage<Void> stage) {
            return Objects.requireNonNull(stage, "close stage").toCompletableFuture();
        }
    }
}
