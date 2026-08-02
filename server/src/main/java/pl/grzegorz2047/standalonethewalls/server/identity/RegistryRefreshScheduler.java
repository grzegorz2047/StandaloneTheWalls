package pl.grzegorz2047.standalonethewalls.server.identity;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.AutomaticRegistryRefreshResult;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshScheduleConfiguration;

/** Single-flight lifecycle for automatic HTTPS registry refresh attempts. */
public final class RegistryRefreshScheduler implements AutoCloseable {
    private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Object monitor = new Object();
    private final RegistryRefreshScheduleConfiguration configuration;
    private final RefreshOperation operation;
    private final TaskScheduler taskScheduler;
    private final JitterSource jitterSource;

    private Status status;
    private ScheduledTask scheduledTask;
    private boolean attemptActive;
    private long ownership;

    private RegistryRefreshScheduler() {
        configuration = RegistryRefreshScheduleConfiguration.DEFAULT;
        operation = () -> AutomaticRegistryRefreshResult.INTERNAL_FAILURE;
        taskScheduler = NoOpTaskScheduler.INSTANCE;
        jitterSource = maximumAbsoluteNanos -> 0L;
        status = Status.disabled();
    }

    private RegistryRefreshScheduler(
            RegistryRefreshScheduleConfiguration configuration,
            RefreshOperation operation,
            TaskScheduler taskScheduler,
            JitterSource jitterSource) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
        status = Status.running(Optional.empty(), 0, Optional.of(configuration.initialDelay()));
        schedule(configuration.initialDelay());
    }

    public static RegistryRefreshScheduler disabled() {
        return new RegistryRefreshScheduler();
    }

    static RegistryRefreshScheduler start(
            RegistryRefreshScheduleConfiguration configuration, RefreshOperation operation) {
        return start(
                configuration,
                operation,
                ExecutorTaskScheduler::new,
                maximumAbsoluteNanos -> {
                    if (maximumAbsoluteNanos == 0L) {
                        return 0L;
                    }
                    return ThreadLocalRandom.current()
                            .nextLong(-maximumAbsoluteNanos, maximumAbsoluteNanos + 1L);
                });
    }

    static RegistryRefreshScheduler start(
            RegistryRefreshScheduleConfiguration configuration,
            RefreshOperation operation,
            Supplier<TaskScheduler> taskSchedulerFactory,
            JitterSource jitterSource) {
        RegistryRefreshScheduleConfiguration schedule =
                Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(taskSchedulerFactory, "taskSchedulerFactory");
        Objects.requireNonNull(jitterSource, "jitterSource");
        if (!schedule.enabled()) {
            return disabled();
        }
        return new RegistryRefreshScheduler(
                schedule,
                operation,
                Objects.requireNonNull(taskSchedulerFactory.get(), "taskScheduler"),
                jitterSource);
    }

    public Status status() {
        synchronized (monitor) {
            return status;
        }
    }

    @Override
    public void close() {
        TaskScheduler schedulerToClose;
        synchronized (monitor) {
            if (status.state() == State.CLOSED) {
                return;
            }
            ownership++;
            if (scheduledTask != null) {
                scheduledTask.cancel();
                scheduledTask = null;
            }
            status =
                    new Status(
                            State.CLOSED,
                            status.lastResult(),
                            status.consecutiveFailures(),
                            Optional.empty());
            schedulerToClose = taskScheduler;
        }
        schedulerToClose.close();
    }

    private void schedule(Duration delay) {
        long expectedOwnership = ownership;
        scheduledTask = taskScheduler.schedule(delay, () -> executeAttempt(expectedOwnership));
    }

    private void executeAttempt(long expectedOwnership) {
        synchronized (monitor) {
            if (status.state() != State.RUNNING
                    || expectedOwnership != ownership
                    || attemptActive) {
                return;
            }
            attemptActive = true;
            scheduledTask = null;
            status =
                    Status.running(
                            status.lastResult(), status.consecutiveFailures(), Optional.empty());
        }

        AutomaticRegistryRefreshResult result;
        try {
            result = Objects.requireNonNull(operation.refresh(), "refresh result");
        } catch (RuntimeException exception) {
            result = AutomaticRegistryRefreshResult.INTERNAL_FAILURE;
        }

        synchronized (monitor) {
            attemptActive = false;
            if (status.state() != State.RUNNING || expectedOwnership != ownership) {
                return;
            }
            int failures =
                    result.successful()
                            ? 0
                            : saturatedIncrement(status.consecutiveFailures());
            Duration baseDelay =
                    result.successful()
                            ? configuration.successInterval()
                            : failureBackoff(failures);
            Duration nextDelay = jitteredDelay(baseDelay, !result.successful());
            status = Status.running(Optional.of(result), failures, Optional.of(nextDelay));
            schedule(nextDelay);
        }
    }

    private Duration failureBackoff(int failures) {
        long maximum = configuration.maximumFailureBackoff().toNanos();
        long delay = configuration.initialFailureBackoff().toNanos();
        int remainingDoublings = failures - 1;
        while (remainingDoublings > 0 && delay < maximum) {
            delay = delay > maximum / 2L ? maximum : Math.min(maximum, delay * 2L);
            remainingDoublings--;
        }
        return Duration.ofNanos(delay);
    }

    private Duration jitteredDelay(Duration baseDelay, boolean failureDelay) {
        long base = baseDelay.toNanos();
        long maximumJitter = configuration.maximumJitter().toNanos();
        long supplied = jitterSource.offsetNanos(maximumJitter);
        long boundedOffset = Math.max(-maximumJitter, Math.min(maximumJitter, supplied));
        long minimum = RegistryRefreshScheduleConfiguration.MINIMUM_RETRY_DELAY.toNanos();
        long maximum =
                failureDelay
                        ? configuration.maximumFailureBackoff().toNanos()
                        : RegistryRefreshScheduleConfiguration.MAXIMUM_DELAY.toNanos();
        long candidate = safeAdd(base, boundedOffset);
        return Duration.ofNanos(Math.max(minimum, Math.min(maximum, candidate)));
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static int saturatedIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    @FunctionalInterface
    interface RefreshOperation {
        AutomaticRegistryRefreshResult refresh();
    }

    @FunctionalInterface
    public interface JitterSource {
        long offsetNanos(long maximumAbsoluteNanos);
    }

    interface TaskScheduler extends AutoCloseable {
        ScheduledTask schedule(Duration delay, Runnable task);

        @Override
        void close();
    }

    @FunctionalInterface
    interface ScheduledTask {
        void cancel();
    }

    public enum State {
        DISABLED,
        RUNNING,
        CLOSED
    }

    public record Status(
            State state,
            Optional<AutomaticRegistryRefreshResult> lastResult,
            int consecutiveFailures,
            Optional<Duration> nextAttemptDelay) {
        public Status {
            state = Objects.requireNonNull(state, "state");
            lastResult = Objects.requireNonNull(lastResult, "lastResult");
            nextAttemptDelay = Objects.requireNonNull(nextAttemptDelay, "nextAttemptDelay");
            if (consecutiveFailures < 0) {
                throw new IllegalArgumentException("consecutiveFailures cannot be negative");
            }
            if (state != State.RUNNING && nextAttemptDelay.isPresent()) {
                throw new IllegalArgumentException(
                        "only a running scheduler can expose a next-attempt delay");
            }
            nextAttemptDelay.ifPresent(
                    delay -> {
                        if (delay.isNegative()) {
                            throw new IllegalArgumentException(
                                    "nextAttemptDelay cannot be negative");
                        }
                    });
        }

        private static Status disabled() {
            return new Status(State.DISABLED, Optional.empty(), 0, Optional.empty());
        }

        private static Status running(
                Optional<AutomaticRegistryRefreshResult> lastResult,
                int consecutiveFailures,
                Optional<Duration> nextAttemptDelay) {
            return new Status(State.RUNNING, lastResult, consecutiveFailures, nextAttemptDelay);
        }
    }

    private enum NoOpTaskScheduler implements TaskScheduler {
        INSTANCE;

        @Override
        public ScheduledTask schedule(Duration delay, Runnable task) {
            throw new IllegalStateException("disabled scheduler cannot schedule work");
        }

        @Override
        public void close() {}
    }

    private static final class ExecutorTaskScheduler implements TaskScheduler {
        private final ScheduledThreadPoolExecutor executor;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ExecutorTaskScheduler() {
            executor =
                    new ScheduledThreadPoolExecutor(
                            1,
                            runnable ->
                                    Thread.ofPlatform()
                                            .name("sunderfront-registry-refresh")
                                            .unstarted(runnable));
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledTask schedule(Duration delay, Runnable task) {
            ScheduledFuture<?> future =
                    executor.schedule(
                            Objects.requireNonNull(task, "task"),
                            Objects.requireNonNull(delay, "delay").toNanos(),
                            TimeUnit.NANOSECONDS);
            return () -> future.cancel(true);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(
                        EXECUTOR_SHUTDOWN_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw new IllegalStateException(
                            "registry refresh executor did not terminate after interruption");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "interrupted while closing registry refresh executor", exception);
            }
        }
    }
}
