package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.AutomaticRegistryRefreshResult;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshScheduleConfiguration;

class RegistryRefreshSchedulerTest {
    @Test
    void disabledSchedulerDoesNotConstructBackendOrExecuteOperationAndClosesIdempotently() {
        AtomicInteger backends = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        ManualTaskScheduler manual = new ManualTaskScheduler();

        RegistryRefreshScheduler scheduler =
                RegistryRefreshScheduler.start(
                        schedule(false, 5L, 20L, 2L, 8L, 0L),
                        () -> {
                            attempts.incrementAndGet();
                            return AutomaticRegistryRefreshResult.ACTIVATED;
                        },
                        () -> {
                            backends.incrementAndGet();
                            return manual;
                        },
                        maximum -> 0L);

        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.DISABLED);
        assertThat(backends).hasValue(0);
        assertThat(attempts).hasValue(0);

        scheduler.close();
        scheduler.close();

        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.CLOSED);
        assertThat(manual.closeCalls()).isZero();
    }

    @Test
    void activationAndUnchangedResetFailuresAndUseSuccessIntervalFromAttemptCompletion() {
        ManualTaskScheduler manual = new ManualTaskScheduler();
        Deque<AutomaticRegistryRefreshResult> results =
                new ArrayDeque<>(
                        List.of(
                                AutomaticRegistryRefreshResult.PROVIDER_FAILURE,
                                AutomaticRegistryRefreshResult.ACTIVATED,
                                AutomaticRegistryRefreshResult.UNCHANGED));
        RegistryRefreshScheduler scheduler =
                start(manual, schedule(true, 5L, 20L, 2L, 8L, 0L), results::removeFirst);

        assertThat(manual.nextDelay()).isEqualTo(Duration.ofSeconds(5));
        manual.runNext();
        assertStatus(
                scheduler,
                AutomaticRegistryRefreshResult.PROVIDER_FAILURE,
                1,
                Duration.ofSeconds(2));
        manual.runNext();
        assertStatus(
                scheduler,
                AutomaticRegistryRefreshResult.ACTIVATED,
                0,
                Duration.ofSeconds(20));
        manual.runNext();
        assertStatus(
                scheduler,
                AutomaticRegistryRefreshResult.UNCHANGED,
                0,
                Duration.ofSeconds(20));
        scheduler.close();
    }

    @Test
    void exponentialBackoffSaturatesWithoutOverflowAndResetsAfterSuccess() {
        ManualTaskScheduler manual = new ManualTaskScheduler();
        Deque<AutomaticRegistryRefreshResult> results = new ArrayDeque<>();
        for (int index = 0; index < 6; index++) {
            results.add(AutomaticRegistryRefreshResult.SNAPSHOT_REJECTED);
        }
        results.add(AutomaticRegistryRefreshResult.ACTIVATED);
        results.add(AutomaticRegistryRefreshResult.CACHE_FAILURE);
        RegistryRefreshScheduler scheduler =
                start(manual, schedule(true, 0L, 30L, 2L, 8L, 0L), results::removeFirst);

        for (Duration expected :
                List.of(
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(4),
                        Duration.ofSeconds(8),
                        Duration.ofSeconds(8),
                        Duration.ofSeconds(8),
                        Duration.ofSeconds(8))) {
            manual.runNext();
            assertThat(scheduler.status().nextAttemptDelay()).contains(expected);
        }
        manual.runNext();
        assertStatus(
                scheduler,
                AutomaticRegistryRefreshResult.ACTIVATED,
                0,
                Duration.ofSeconds(30));
        manual.runNext();
        assertStatus(
                scheduler,
                AutomaticRegistryRefreshResult.CACHE_FAILURE,
                1,
                Duration.ofSeconds(2));
        scheduler.close();
    }

    @Test
    void jitterIsBoundedAtBothEdgesAndNeverCrossesSafeMinimumOrMaximumBackoff() {
        ManualTaskScheduler negative = new ManualTaskScheduler();
        RegistryRefreshScheduler lower =
                RegistryRefreshScheduler.start(
                        schedule(true, 0L, 10L, 2L, 8L, 3L),
                        () -> AutomaticRegistryRefreshResult.PROVIDER_FAILURE,
                        () -> negative,
                        maximum -> -maximum);
        negative.runNext();
        assertThat(lower.status().nextAttemptDelay()).contains(Duration.ofSeconds(1));
        lower.close();

        ManualTaskScheduler positive = new ManualTaskScheduler();
        RegistryRefreshScheduler upper =
                RegistryRefreshScheduler.start(
                        schedule(true, 0L, 10L, 8L, 8L, 3L),
                        () -> AutomaticRegistryRefreshResult.PROVIDER_FAILURE,
                        () -> positive,
                        maximum -> Long.MAX_VALUE);
        positive.runNext();
        assertThat(upper.status().nextAttemptDelay()).contains(Duration.ofSeconds(8));
        upper.close();
    }

    @Test
    void everySemanticFailureUsesRetryBackoff() {
        for (AutomaticRegistryRefreshResult result : AutomaticRegistryRefreshResult.values()) {
            if (result.successful()) {
                continue;
            }
            ManualTaskScheduler manual = new ManualTaskScheduler();
            RegistryRefreshScheduler scheduler =
                    start(manual, schedule(true, 0L, 10L, 3L, 9L, 0L), () -> result);

            manual.runNext();

            assertStatus(scheduler, result, 1, Duration.ofSeconds(3));
            scheduler.close();
        }
    }

    @Test
    void duplicateTaskDispatchCannotOverlapAttemptsOrScheduleFromNominalStart()
            throws Exception {
        ManualTaskScheduler manual = new ManualTaskScheduler();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RegistryRefreshScheduler scheduler =
                start(
                        manual,
                        schedule(true, 0L, 10L, 2L, 8L, 0L),
                        () -> {
                            calls.incrementAndGet();
                            int current = active.incrementAndGet();
                            maximumActive.accumulateAndGet(current, Math::max);
                            entered.countDown();
                            await(release);
                            active.decrementAndGet();
                            return AutomaticRegistryRefreshResult.ACTIVATED;
                        });
        Runnable task = manual.peekTask();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(task);
            assertThat(entered.await(5L, TimeUnit.SECONDS)).isTrue();
            Future<?> duplicate = executor.submit(task);
            duplicate.get(5L, TimeUnit.SECONDS);
            assertThat(manual.pendingCount()).isOne();
            release.countDown();
            first.get(5L, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(calls).hasValue(1);
        assertThat(maximumActive).hasValue(1);
        assertThat(manual.pendingCount()).isEqualTo(2);
        scheduler.close();
    }

    @Test
    void shutdownWhileWaitingCancelsTaskWithoutExecutingIt() {
        ManualTaskScheduler manual = new ManualTaskScheduler();
        AtomicInteger calls = new AtomicInteger();
        RegistryRefreshScheduler scheduler =
                start(
                        manual,
                        schedule(true, 60L, 10L, 2L, 8L, 0L),
                        () -> {
                            calls.incrementAndGet();
                            return AutomaticRegistryRefreshResult.ACTIVATED;
                        });

        scheduler.close();
        manual.runNext();

        assertThat(calls).hasValue(0);
        assertThat(manual.closeCalls()).isOne();
        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.CLOSED);
    }

    @Test
    void shutdownInterruptsActiveProviderCallSuppressesItsResultAndLeavesNoWorker()
            throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        RegistryRefreshScheduler scheduler =
                RegistryRefreshScheduler.start(
                        schedule(true, 0L, 10L, 2L, 8L, 0L),
                        () -> {
                            entered.countDown();
                            try {
                                new CountDownLatch(1).await();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                interrupted.countDown();
                            }
                            return AutomaticRegistryRefreshResult.PROVIDER_FAILURE;
                        });
        assertThat(entered.await(5L, TimeUnit.SECONDS)).isTrue();

        scheduler.close();
        scheduler.close();

        assertThat(interrupted.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.status().state()).isEqualTo(RegistryRefreshScheduler.State.CLOSED);
        assertThat(scheduler.status().lastResult()).isEmpty();
        assertThat(
                        Thread.getAllStackTraces().keySet().stream()
                                .filter(Thread::isAlive)
                                .map(Thread::getName))
                .doesNotContain("sunderfront-registry-refresh");
    }

    @Test
    void productionAttemptRunsOutsideCallerAndSimulationThread() throws InterruptedException {
        AtomicReference<String> attemptThread = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        RegistryRefreshScheduler scheduler =
                RegistryRefreshScheduler.start(
                        schedule(true, 0L, 10L, 2L, 8L, 0L),
                        () -> {
                            attemptThread.set(Thread.currentThread().getName());
                            completed.countDown();
                            return AutomaticRegistryRefreshResult.UNCHANGED;
                        });

        assertThat(completed.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(attemptThread.get()).isEqualTo("sunderfront-registry-refresh");
        assertThat(attemptThread.get()).doesNotContain("simulation");
        scheduler.close();
    }

    private static RegistryRefreshScheduler start(
            ManualTaskScheduler manual,
            RegistryRefreshScheduleConfiguration configuration,
            RegistryRefreshScheduler.RefreshOperation operation) {
        return RegistryRefreshScheduler.start(
                configuration, operation, () -> manual, maximum -> 0L);
    }

    private static RegistryRefreshScheduleConfiguration schedule(
            boolean enabled,
            long initialDelaySeconds,
            long successIntervalSeconds,
            long initialFailureSeconds,
            long maximumFailureSeconds,
            long jitterSeconds) {
        return new RegistryRefreshScheduleConfiguration(
                enabled,
                Duration.ofSeconds(initialDelaySeconds),
                Duration.ofSeconds(successIntervalSeconds),
                Duration.ofSeconds(initialFailureSeconds),
                Duration.ofSeconds(maximumFailureSeconds),
                Duration.ofSeconds(jitterSeconds));
    }

    private static void assertStatus(
            RegistryRefreshScheduler scheduler,
            AutomaticRegistryRefreshResult result,
            int failures,
            Duration nextDelay) {
        RegistryRefreshScheduler.Status status = scheduler.status();
        assertThat(status.state()).isEqualTo(RegistryRefreshScheduler.State.RUNNING);
        assertThat(status.lastResult()).contains(result);
        assertThat(status.consecutiveFailures()).isEqualTo(failures);
        assertThat(status.nextAttemptDelay()).contains(nextDelay);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test latch interrupted", exception);
        }
    }

    private static final class ManualTaskScheduler
            implements RegistryRefreshScheduler.TaskScheduler {
        private final Deque<Entry> entries = new ArrayDeque<>();
        private int closeCalls;

        @Override
        public RegistryRefreshScheduler.ScheduledTask schedule(Duration delay, Runnable task) {
            Entry entry = new Entry(delay, task);
            entries.addLast(entry);
            return () -> entry.cancelled = true;
        }

        @Override
        public void close() {
            closeCalls++;
            entries.forEach(entry -> entry.cancelled = true);
        }

        private void runNext() {
            Entry entry = entries.removeFirst();
            if (!entry.cancelled) {
                entry.task.run();
            }
        }

        private Runnable peekTask() {
            return entries.getFirst().task;
        }

        private Duration nextDelay() {
            return entries.getLast().delay;
        }

        private int pendingCount() {
            return entries.size();
        }

        private int closeCalls() {
            return closeCalls;
        }

        private static final class Entry {
            private final Duration delay;
            private final Runnable task;
            private boolean cancelled;

            private Entry(Duration delay, Runnable task) {
                this.delay = delay;
                this.task = task;
            }
        }
    }
}
