package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.profile.AppStep;
import com.jme3.renderer.Renderer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JmeGpuFrameTimeProfilerTest {
    @Test
    void unsupportedRendererStaysDisabledWithoutStartingProfiling() {
        FakeRenderer backend = new FakeRenderer(false);
        JmeGpuFrameTimeProfiler profiler =
                JmeGpuFrameTimeProfiler.create(
                        backend.renderer(), () -> GraphicsBenchmarkSession.Phase.MEASURING);

        assertThat(profiler.enabled()).isFalse();
        profiler.appStep(AppStep.BeginFrame);
        profiler.appStep(AppStep.RenderFrame);
        profiler.appStep(AppStep.EndFrame);

        assertThat(profiler.poll()).isEmpty();
        assertThat(backend.generateCalls).isOne();
        assertThat(backend.startCalls).isZero();
        assertThat(backend.stopCalls).isZero();

        profiler.close();
        profiler.close();
    }

    @Test
    void delayedResultsKeepTheirOriginPhaseAndWarmUpCannotLeakIntoMeasurement() {
        FakeRenderer backend = new FakeRenderer(true);
        AtomicReference<GraphicsBenchmarkSession.Phase> phase =
                new AtomicReference<>(GraphicsBenchmarkSession.Phase.WARM_UP);
        JmeGpuFrameTimeProfiler profiler =
                JmeGpuFrameTimeProfiler.create(backend.renderer(), phase::get);

        profiler.appStep(AppStep.RenderFrame);
        profiler.appStep(AppStep.EndFrame);
        int warmUpTask = backend.startedTaskIds.get(0);

        phase.set(GraphicsBenchmarkSession.Phase.MEASURING);
        profiler.appStep(AppStep.BeginFrame);
        assertThat(profiler.poll()).isEmpty();
        profiler.appStep(AppStep.RenderFrame);
        profiler.appStep(AppStep.EndFrame);
        int measurementTask = backend.startedTaskIds.get(1);

        backend.complete(warmUpTask, 7_000_000L);
        profiler.appStep(AppStep.BeginFrame);
        assertThat(profiler.poll()).isEmpty();

        backend.complete(measurementTask, 9_000_000L);
        profiler.appStep(AppStep.BeginFrame);
        assertThat(profiler.poll()).hasValue(9_000_000L);
        assertThat(profiler.poll()).isEmpty();

        profiler.close();
    }

    @Test
    void unavailableQueriesStayBoundedAndInvalidDurationsAreDropped() {
        FakeRenderer backend = new FakeRenderer(true);
        JmeGpuFrameTimeProfiler profiler =
                JmeGpuFrameTimeProfiler.create(
                        backend.renderer(), () -> GraphicsBenchmarkSession.Phase.MEASURING);

        for (int index = 0; index < JmeGpuFrameTimeProfiler.MAXIMUM_IN_FLIGHT_QUERIES; index++) {
            profiler.appStep(AppStep.RenderFrame);
            profiler.appStep(AppStep.EndFrame);
            profiler.appStep(AppStep.BeginFrame);
        }
        assertThat(backend.startCalls).isEqualTo(JmeGpuFrameTimeProfiler.MAXIMUM_IN_FLIGHT_QUERIES);

        profiler.appStep(AppStep.RenderFrame);
        profiler.appStep(AppStep.EndFrame);
        assertThat(backend.startCalls).isEqualTo(JmeGpuFrameTimeProfiler.MAXIMUM_IN_FLIGHT_QUERIES);

        int firstTask = backend.startedTaskIds.get(0);
        backend.complete(firstTask, 0L);
        profiler.appStep(AppStep.BeginFrame);
        assertThat(profiler.poll()).isEmpty();

        profiler.appStep(AppStep.RenderFrame);
        profiler.appStep(AppStep.EndFrame);
        int reusedTask = backend.startedTaskIds.get(backend.startedTaskIds.size() - 1);
        assertThat(reusedTask).isEqualTo(firstTask);
        backend.complete(reusedTask, 11_000_000L);
        profiler.appStep(AppStep.BeginFrame);
        assertThat(profiler.poll()).hasValue(11_000_000L);

        profiler.close();
    }

    @Test
    void closeStopsAnActiveQueryOnceAndIsIdempotent() {
        FakeRenderer backend = new FakeRenderer(true);
        JmeGpuFrameTimeProfiler profiler =
                JmeGpuFrameTimeProfiler.create(
                        backend.renderer(), () -> GraphicsBenchmarkSession.Phase.MEASURING);

        profiler.appStep(AppStep.RenderFrame);
        assertThat(backend.startCalls).isOne();

        profiler.close();
        profiler.close();

        assertThat(backend.stopCalls).isOne();
        assertThat(profiler.poll()).isEmpty();
        profiler.appStep(AppStep.RenderFrame);
        assertThat(backend.startCalls).isOne();
    }

    private static final class FakeRenderer {
        private final int[] taskIds;
        private final List<Integer> startedTaskIds = new ArrayList<>();
        private final Set<Integer> readyTaskIds = new HashSet<>();
        private final Map<Integer, Long> durations = new HashMap<>();
        private final Renderer renderer;
        private int generateCalls;
        private int startCalls;
        private int stopCalls;

        private FakeRenderer(boolean supported) {
            taskIds = supported ? new int[] {11, 12, 13, 14} : new int[0];
            renderer =
                    (Renderer)
                            Proxy.newProxyInstance(
                                    Renderer.class.getClassLoader(),
                                    new Class<?>[] {Renderer.class},
                                    (proxy, method, arguments) -> {
                                        switch (method.getName()) {
                                            case "generateProfilingTasks":
                                                generateCalls++;
                                                return taskIds.clone();
                                            case "startProfiling":
                                                int taskId = (Integer) arguments[0];
                                                startCalls++;
                                                startedTaskIds.add(taskId);
                                                return null;
                                            case "stopProfiling":
                                                stopCalls++;
                                                return null;
                                            case "isTaskResultAvailable":
                                                return readyTaskIds.contains(
                                                        (Integer) arguments[0]);
                                            case "getProfilingTime":
                                                return durations.getOrDefault(
                                                        (Integer) arguments[0], 0L);
                                            case "toString":
                                                return "FakeRenderer";
                                            default:
                                                throw new UnsupportedOperationException(
                                                        "Unexpected renderer call: "
                                                                + method.getName());
                                        }
                                    });
        }

        private Renderer renderer() {
            return renderer;
        }

        private void complete(int taskId, long durationNanos) {
            durations.put(taskId, durationNanos);
            readyTaskIds.add(taskId);
        }
    }
}
