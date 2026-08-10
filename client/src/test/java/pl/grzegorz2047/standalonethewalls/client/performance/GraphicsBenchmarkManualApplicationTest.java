package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jme3.scene.Node;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkManualApplicationTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final GraphicsBenchmarkCompatibilityKey KEY =
            new GraphicsBenchmarkCompatibilityKey(
                    "bundled-assets-lock",
                    "sha256:" + "a".repeat(64),
                    GraphicsBenchmarkReferenceScene.SCENARIO_ID,
                    GraphicsBenchmarkReferenceScene.SCENARIO_VERSION);

    @TempDir Path tempDirectory;

    @Test
    void headlessLifecyclePersistsOneCompleteSchemaV2Report()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path reportDirectory = tempDirectory.resolve("reports");
        GraphicsBenchmarkReportStore store = new GraphicsBenchmarkReportStore(reportDirectory);
        FakeTelemetrySource source = new FakeTelemetrySource();
        GraphicsBenchmarkManualApplication application = application(source, store, 1.0d);
        GraphicsBenchmarkSession.Outcome outcome;

        try {
            application.start(JmeContext.Type.Headless, true);
            outcome = application.awaitCompletion(Duration.ofSeconds(5));
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }

        assertThat(source.closed).isTrue();
        assertThat(outcome.report().repositoryCommit()).isEqualTo(COMMIT);
        assertThat(outcome.report().measuredPreset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(outcome.report().result().width()).isEqualTo(1280);
        assertThat(outcome.report().result().height()).isEqualTo(720);
        assertThat(outcome.report().result().renderScale()).isEqualTo(1.0d);
        assertThat(GraphicsBenchmarkCompatibilityKey.fromReport(outcome.report())).isEqualTo(KEY);
        assertThat(outcome.report().telemetrySummary()).isEqualTo(outcome.telemetrySummary());
        assertThat(outcome.telemetrySummary().peakResidentMemoryBytes()).isEqualTo(128L);
        assertThat(outcome.telemetrySummary().peakDrawCalls()).isEqualTo(12);
        assertThat(outcome.telemetrySummary().peakRenderedObjectCount()).isEqualTo(34);
        assertThat(outcome.telemetrySummary().gpuSampleCount()).isZero();
        assertThat(store.load()).contains(GraphicsBenchmarkReportJson.serialize(outcome.report()));
        assertThat(Files.readString(store.reportFile(), StandardCharsets.UTF_8))
                .isEqualTo(GraphicsBenchmarkReportJson.serialize(outcome.report()));
        try (Stream<Path> files = Files.list(reportDirectory)) {
            assertThat(files.map(reportDirectory::relativize).map(Path::toString).toList())
                    .containsExactly(GraphicsBenchmarkReportStore.FILE_NAME);
        }
    }

    @Test
    void headlessScaledFramebufferLifecyclePersistsTheActualRenderScale()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path reportDirectory = tempDirectory.resolve("scaled-reports");
        GraphicsBenchmarkReportStore store = new GraphicsBenchmarkReportStore(reportDirectory);
        FakeTelemetrySource source = new FakeTelemetrySource(1);
        GraphicsBenchmarkManualApplication application = application(source, store, 0.75d);
        GraphicsBenchmarkSession.Outcome outcome;

        try {
            application.start(JmeContext.Type.Headless, true);
            outcome = application.awaitCompletion(Duration.ofSeconds(5));
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }

        assertThat(source.closed).isTrue();
        assertThat(outcome.report().result().width()).isEqualTo(1280);
        assertThat(outcome.report().result().height()).isEqualTo(720);
        assertThat(outcome.report().result().renderScale()).isEqualTo(0.75d);
        assertThat(store.load()).contains(GraphicsBenchmarkReportJson.serialize(outcome.report()));
    }

    @Test
    void persistenceFailureCompletesWithBoundedFailure() throws IOException {
        Path blockedOutputDirectory = tempDirectory.resolve("blocked-output");
        assertThat(
                        Files.writeString(
                                blockedOutputDirectory, "not-a-directory", StandardCharsets.UTF_8))
                .isEqualTo(blockedOutputDirectory);
        GraphicsBenchmarkReportStore store =
                new GraphicsBenchmarkReportStore(blockedOutputDirectory);
        FakeTelemetrySource source = new FakeTelemetrySource();
        GraphicsBenchmarkManualApplication application = application(source, store, 1.0d);

        try {
            application.start(JmeContext.Type.Headless, true);
            assertThatThrownBy(() -> application.awaitCompletion(Duration.ofSeconds(5)))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
        assertThat(source.closed).isTrue();
    }

    private static GraphicsBenchmarkManualApplication application(
            FakeTelemetrySource source, GraphicsBenchmarkReportStore store, double renderScale) {
        GraphicsBenchmarkSession.Config config =
                new GraphicsBenchmarkSession.Config(
                        COMMIT, KEY, GraphicsQualityPreset.LOW, 1280, 720, renderScale, 0, 1);
        GraphicsBenchmarkRunState state =
                new GraphicsBenchmarkRunState(
                        config,
                        Optional.empty(),
                        (ignoredAssetManager, ignoredPreset) ->
                                new Node(GraphicsBenchmarkReferenceScene.ROOT_NAME),
                        (ignoredRenderer, ignoredGpuFrameTimeSource) -> source);
        return new GraphicsBenchmarkManualApplication(state, store, renderScale);
    }

    private static final class FakeTelemetrySource
            implements GraphicsBenchmarkRunState.TelemetrySource {
        private int emptySamplesRemaining;
        private boolean emitted;
        private boolean closed;

        private FakeTelemetrySource() {
            this(0);
        }

        private FakeTelemetrySource(int emptySamplesRemaining) {
            this.emptySamplesRemaining = emptySamplesRemaining;
        }

        @Override
        public Optional<GraphicsTelemetrySample> sample(float timePerFrame, Node benchmarkScene) {
            if (emptySamplesRemaining > 0) {
                emptySamplesRemaining--;
                return Optional.empty();
            }
            if (emitted) {
                return Optional.empty();
            }
            emitted = true;
            return Optional.of(
                    new GraphicsTelemetrySample(20_000_000L, OptionalLong.empty(), 128L, 12, 34));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
