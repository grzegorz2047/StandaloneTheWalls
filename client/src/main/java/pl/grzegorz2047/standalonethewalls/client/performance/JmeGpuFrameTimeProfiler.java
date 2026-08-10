package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.profile.AppProfiler;
import com.jme3.profile.AppStep;
import com.jme3.profile.SpStep;
import com.jme3.profile.VpStep;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** Bounded non-blocking jME GPU timer-query bridge for benchmark render frames. */
final class JmeGpuFrameTimeProfiler implements AppProfiler, GpuFrameTimeSource, AutoCloseable {
    static final int MAXIMUM_IN_FLIGHT_QUERIES = 4;

    private final Renderer renderer;
    private final Supplier<GraphicsBenchmarkSession.Phase> phaseSupplier;
    private final Deque<Integer> availableTaskIds = new ArrayDeque<>();
    private final Deque<Query> inFlightQueries = new ArrayDeque<>();
    private final Deque<Long> readyMeasurementTimes = new ArrayDeque<>();
    private ActiveQuery activeQuery;
    private boolean enabled;
    private boolean closed;

    static JmeGpuFrameTimeProfiler create(
            Renderer renderer, Supplier<GraphicsBenchmarkSession.Phase> phaseSupplier) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(phaseSupplier, "phaseSupplier");
        int[] taskIds;
        try {
            taskIds = renderer.generateProfilingTasks(MAXIMUM_IN_FLIGHT_QUERIES);
        } catch (RuntimeException exception) {
            taskIds = new int[0];
        }
        return new JmeGpuFrameTimeProfiler(renderer, phaseSupplier, taskIds);
    }

    JmeGpuFrameTimeProfiler(
            Renderer renderer,
            Supplier<GraphicsBenchmarkSession.Phase> phaseSupplier,
            int[] taskIds) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.phaseSupplier = Objects.requireNonNull(phaseSupplier, "phaseSupplier");
        Objects.requireNonNull(taskIds, "taskIds");
        if (taskIds.length > MAXIMUM_IN_FLIGHT_QUERIES) {
            throw new IllegalArgumentException("too many GPU profiling task ids");
        }
        for (int taskId : taskIds) {
            if (taskId <= 0 || availableTaskIds.contains(taskId)) {
                throw new IllegalArgumentException("invalid GPU profiling task id");
            }
            availableTaskIds.addLast(taskId);
        }
        enabled = !availableTaskIds.isEmpty();
    }

    boolean enabled() {
        return enabled && !closed;
    }

    @Override
    public void appStep(AppStep step) {
        Objects.requireNonNull(step, "step");
        if (!enabled()) {
            return;
        }
        switch (step) {
            case BeginFrame -> collectReadyResults();
            case RenderFrame -> beginRenderFrame();
            case EndFrame -> endRenderFrame();
            default -> {
                // No benchmark timer work for this application step.
            }
        }
    }

    @Override
    public void appSubStep(String... additionalInfo) {}

    @Override
    public void vpStep(VpStep step, ViewPort viewPort, RenderQueue.Bucket bucket) {}

    @Override
    public void spStep(SpStep step, String... additionalInfo) {}

    @Override
    public OptionalLong poll() {
        if (closed || readyMeasurementTimes.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(readyMeasurementTimes.removeFirst());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        enabled = false;
        if (activeQuery != null) {
            try {
                renderer.stopProfiling();
            } catch (RuntimeException ignored) {
                // The renderer owns native profiling tasks until context teardown.
            }
            activeQuery = null;
        }
        availableTaskIds.clear();
        inFlightQueries.clear();
        readyMeasurementTimes.clear();
    }

    private void beginRenderFrame() {
        if (activeQuery != null || availableTaskIds.isEmpty()) {
            return;
        }
        GraphicsBenchmarkSession.Phase phase =
                Objects.requireNonNull(phaseSupplier.get(), "benchmark phase");
        if (phase == GraphicsBenchmarkSession.Phase.COMPLETE) {
            return;
        }
        int taskId = availableTaskIds.removeFirst();
        try {
            renderer.startProfiling(taskId);
            activeQuery = new ActiveQuery(taskId, phase);
        } catch (RuntimeException exception) {
            availableTaskIds.addFirst(taskId);
            disable();
        }
    }

    private void endRenderFrame() {
        if (activeQuery == null) {
            return;
        }
        ActiveQuery completed = activeQuery;
        activeQuery = null;
        try {
            renderer.stopProfiling();
            inFlightQueries.addLast(new Query(completed.taskId(), completed.phase()));
        } catch (RuntimeException exception) {
            availableTaskIds.addLast(completed.taskId());
            disable();
        }
    }

    private void collectReadyResults() {
        Iterator<Query> iterator = inFlightQueries.iterator();
        while (iterator.hasNext()) {
            Query query = iterator.next();
            final boolean available;
            try {
                available = renderer.isTaskResultAvailable(query.taskId());
            } catch (RuntimeException exception) {
                disable();
                return;
            }
            if (!available) {
                continue;
            }

            final long durationNanos;
            try {
                durationNanos = renderer.getProfilingTime(query.taskId());
            } catch (RuntimeException exception) {
                disable();
                return;
            }
            iterator.remove();
            availableTaskIds.addLast(query.taskId());
            if (query.phase() != GraphicsBenchmarkSession.Phase.MEASURING) {
                continue;
            }
            if (durationNanos <= 0L || durationNanos > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
                continue;
            }
            if (readyMeasurementTimes.size() < MAXIMUM_IN_FLIGHT_QUERIES) {
                readyMeasurementTimes.addLast(durationNanos);
            }
        }
    }

    private void disable() {
        enabled = false;
        activeQuery = null;
        availableTaskIds.clear();
        inFlightQueries.clear();
        readyMeasurementTimes.clear();
    }

    private record ActiveQuery(int taskId, GraphicsBenchmarkSession.Phase phase) {}

    private record Query(int taskId, GraphicsBenchmarkSession.Phase phase) {}
}
