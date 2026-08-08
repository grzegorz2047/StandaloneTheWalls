package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GraphicsTelemetrySummaryTest {
    @Test
    void summarizesCpuPartialGpuCoverageAndPeakCounters() {
        List<GraphicsTelemetrySample> samples =
                List.of(
                        sample(10_000_000L, 9_000_000L, 100L, 100, 1_000),
                        sampleWithoutGpu(20_000_000L, 200L, 80, 1_100),
                        sample(30_000_000L, 18_000_000L, 150L, 120, 900),
                        sample(40_000_000L, 27_000_000L, 180L, 90, 1_500));

        GraphicsTelemetrySummary summary = GraphicsTelemetrySummary.fromSamples(samples);

        assertThat(summary.sampleCount()).isEqualTo(4);
        assertThat(summary.cpuFrameTime())
                .isEqualTo(new FrameTimeStatistics(4, 25_000_000L, 40_000_000L, 40_000_000L));
        assertThat(summary.gpuSampleCount()).isEqualTo(3);
        assertThat(summary.gpuFrameTime())
                .contains(new FrameTimeStatistics(3, 18_000_000L, 27_000_000L, 27_000_000L));
        assertThat(summary.gpuCoverageRatio()).isEqualTo(0.75d);
        assertThat(summary.peakResidentMemoryBytes()).isEqualTo(200L);
        assertThat(summary.peakDrawCalls()).isEqualTo(120);
        assertThat(summary.peakRenderedObjectCount()).isEqualTo(1_500);
    }

    @Test
    void representsUnavailableGpuTimingWithoutFabricatingStatistics() {
        GraphicsTelemetrySummary summary =
                GraphicsTelemetrySummary.fromSamples(
                        List.of(
                                sampleWithoutGpu(10_000_000L, 100L, 10, 100),
                                sampleWithoutGpu(20_000_000L, 200L, 20, 200)));

        assertThat(summary.gpuFrameTime()).isEmpty();
        assertThat(summary.gpuSampleCount()).isZero();
        assertThat(summary.gpuCoverageRatio()).isZero();
    }

    @Test
    void rejectsEmptyOversizedAndNullSampleWindows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GraphicsTelemetrySummary.fromSamples(List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsTelemetrySummary.fromSamples(
                                        Collections.nCopies(
                                                FrameTimeStatistics.MAXIMUM_SAMPLES + 1,
                                                sampleWithoutGpu(1L, 0L, 0, 0))));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsTelemetrySummary.fromSamples(
                                        java.util.Arrays.asList(
                                                sampleWithoutGpu(1L, 0L, 0, 0), null)));
    }

    @Test
    void rejectsForgedInconsistentSummaryState() {
        FrameTimeStatistics oneSample = new FrameTimeStatistics(1, 1L, 1L, 1L);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsTelemetrySummary(
                                        2, oneSample, Optional.empty(), 0, 0L, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsTelemetrySummary(
                                        1, oneSample, Optional.empty(), 1, 0L, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsTelemetrySummary(
                                        1,
                                        oneSample,
                                        Optional.of(oneSample),
                                        0,
                                        0L,
                                        0,
                                        0));
    }

    private static GraphicsTelemetrySample sample(
            long cpuFrameTimeNanos,
            long gpuFrameTimeNanos,
            long residentMemoryBytes,
            int drawCalls,
            int renderedObjectCount) {
        return new GraphicsTelemetrySample(
                cpuFrameTimeNanos,
                OptionalLong.of(gpuFrameTimeNanos),
                residentMemoryBytes,
                drawCalls,
                renderedObjectCount);
    }

    private static GraphicsTelemetrySample sampleWithoutGpu(
            long cpuFrameTimeNanos,
            long residentMemoryBytes,
            int drawCalls,
            int renderedObjectCount) {
        return new GraphicsTelemetrySample(
                cpuFrameTimeNanos,
                OptionalLong.empty(),
                residentMemoryBytes,
                drawCalls,
                renderedObjectCount);
    }
}
