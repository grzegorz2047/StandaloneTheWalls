package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.scene.Node;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkRunStateTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final GraphicsBenchmarkCompatibilityKey KEY =
            new GraphicsBenchmarkCompatibilityKey(
                    "core",
                    "8",
                    GraphicsBenchmarkReferenceScene.SCENARIO_ID,
                    GraphicsBenchmarkReferenceScene.SCENARIO_VERSION);

    @Test
    void rejectsApplicationsThatDoNotOwnASimpleApplicationSceneGraph() {
        GraphicsBenchmarkRunState state = state(config(0, 1), new FakeTelemetrySource());
        Application application =
                (Application)
                        Proxy.newProxyInstance(
                                Application.class.getClassLoader(),
                                new Class<?>[] {Application.class},
                                (proxy, method, arguments) -> null);

        assertThatIllegalArgumentException().isThrownBy(() -> state.initialize(application));
    }

    @Test
    void runsReferenceSceneThroughWarmUpMeasurementAndStableCompletion() {
        FakeTelemetrySource source =
                new FakeTelemetrySource(
                        Optional.empty(),
                        Optional.of(sample(50_000_000L, 100L, 10, 20)),
                        Optional.of(sample(10_000_000L, 200L, 20, 30)),
                        Optional.of(sample(12_000_000L, 300L, 30, 40)));
        GraphicsBenchmarkRunState state = state(config(1, 2), source);
        TestApplication application = new TestApplication();

        state.initialize(application);

        assertThat(state.benchmarkSceneAttached()).isTrue();
        assertThat(application.getRootNode().getQuantity()).isOne();
        assertThat(state.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.WARM_UP);
        assertThat(state.warmUpFramesRemaining()).isOne();

        state.update(1.0f / 60.0f);
        assertThat(source.sampleCalls).isOne();
        assertThat(state.warmUpFramesRemaining()).isOne();
        assertThat(state.measurementFramesCollected()).isZero();

        state.update(1.0f / 60.0f);
        assertThat(state.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.MEASURING);
        assertThat(state.warmUpFramesRemaining()).isZero();
        assertThat(state.measurementFramesCollected()).isZero();

        state.update(1.0f / 60.0f);
        assertThat(state.measurementFramesCollected()).isOne();
        assertThat(state.measurementFramesRemaining()).isOne();
        assertThat(state.outcome()).isEmpty();

        state.update(1.0f / 60.0f);
        GraphicsBenchmarkSession.Outcome outcome = state.outcome().orElseThrow();
        assertThat(state.phase()).isEqualTo(GraphicsBenchmarkSession.Phase.COMPLETE);
        assertThat(outcome.telemetrySummary().cpuFrameTime())
                .isEqualTo(new FrameTimeStatistics(2, 11_000_000L, 12_000_000L, 12_000_000L));
        assertThat(outcome.telemetrySummary().peakResidentMemoryBytes()).isEqualTo(300L);
        assertThat(outcome.telemetrySummary().peakDrawCalls()).isEqualTo(30);
        assertThat(outcome.telemetrySummary().peakRenderedObjectCount()).isEqualTo(40);
        assertThat(source.sampleCalls).isEqualTo(4);

        state.update(1.0f / 10.0f);
        assertThat(source.sampleCalls).isEqualTo(4);
        assertThat(state.outcome()).contains(outcome);

        state.cleanup(application);
        assertThat(source.closed).isTrue();
        assertThat(state.benchmarkSceneAttached()).isFalse();
        assertThat(application.getRootNode().getQuantity()).isZero();
        assertThat(state.outcome()).contains(outcome);
    }

    @Test
    void closesTelemetrySourceWhenSceneFactoryViolatesDetachedContract() {
        FakeTelemetrySource source = new FakeTelemetrySource();
        Node parent = new Node("parent");
        Node attached = new Node("attached");
        parent.attachChild(attached);
        GraphicsBenchmarkRunState state =
                new GraphicsBenchmarkRunState(
                        config(0, 1),
                        Optional.empty(),
                        ignored -> attached,
                        ignored -> source);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> state.initialize(new TestApplication()));
        assertThat(source.closed).isTrue();
        assertThat(attached.getParent()).isSameAs(parent);
    }

    private static GraphicsBenchmarkRunState state(
            GraphicsBenchmarkSession.Config config, FakeTelemetrySource source) {
        return new GraphicsBenchmarkRunState(
                config,
                Optional.empty(),
                ignored -> new Node(GraphicsBenchmarkReferenceScene.ROOT_NAME),
                ignored -> source);
    }

    private static GraphicsBenchmarkSession.Config config(
            int warmUpFrameCount, int measurementFrameCount) {
        return new GraphicsBenchmarkSession.Config(
                COMMIT,
                KEY,
                GraphicsQualityPreset.MEDIUM,
                1920,
                1080,
                1.0d,
                warmUpFrameCount,
                measurementFrameCount);
    }

    private static GraphicsTelemetrySample sample(
            long cpuNanos, long memoryBytes, int drawCalls, int objectCount) {
        return new GraphicsTelemetrySample(
                cpuNanos, OptionalLong.empty(), memoryBytes, drawCalls, objectCount);
    }

    private static final class TestApplication extends SimpleApplication {
        private TestApplication() {
            super();
        }

        @Override
        public void simpleInitApp() {}
    }

    private static final class FakeTelemetrySource
            implements GraphicsBenchmarkRunState.TelemetrySource {
        private final Deque<Optional<GraphicsTelemetrySample>> samples = new ArrayDeque<>();
        private int sampleCalls;
        private boolean closed;

        @SafeVarargs
        private FakeTelemetrySource(Optional<GraphicsTelemetrySample>... samples) {
            for (Optional<GraphicsTelemetrySample> sample : samples) {
                this.samples.addLast(sample);
            }
        }

        @Override
        public Optional<GraphicsTelemetrySample> sample(float timePerFrame, Node benchmarkScene) {
            sampleCalls++;
            return samples.isEmpty() ? Optional.empty() : samples.removeFirst();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
