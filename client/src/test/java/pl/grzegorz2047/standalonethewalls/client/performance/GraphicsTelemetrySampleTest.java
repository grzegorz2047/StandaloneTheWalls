package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GraphicsTelemetrySampleTest {
    @Test
    void retainsAvailableAndUnavailableGpuTimingExplicitly() {
        GraphicsTelemetrySample measured =
                new GraphicsTelemetrySample(12_000_000L, OptionalLong.of(11_000_000L), 512L, 42, 900);
        GraphicsTelemetrySample unavailable =
                new GraphicsTelemetrySample(13_000_000L, OptionalLong.empty(), 640L, 43, 901);

        assertThat(measured.cpuFrameTimeNanos()).isEqualTo(12_000_000L);
        assertThat(measured.gpuFrameTimeNanos()).hasValue(11_000_000L);
        assertThat(unavailable.gpuFrameTimeNanos()).isEmpty();
        assertThat(measured.residentMemoryBytes()).isEqualTo(512L);
        assertThat(measured.drawCalls()).isEqualTo(42);
        assertThat(measured.renderedObjectCount()).isEqualTo(900);
    }

    @Test
    void rejectsInvalidFrameTimes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sample(0L, OptionalLong.empty(), 0L, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                sample(
                                        FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L,
                                        OptionalLong.empty(),
                                        0L,
                                        0,
                                        0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sample(1L, OptionalLong.of(0L), 0L, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                sample(
                                        1L,
                                        OptionalLong.of(
                                                FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L),
                                        0L,
                                        0,
                                        0));
    }

    @Test
    void rejectsInvalidMemoryAndCounters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sample(1L, OptionalLong.empty(), -1L, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                sample(
                                        1L,
                                        OptionalLong.empty(),
                                        GraphicsTelemetrySample.MAXIMUM_RESIDENT_MEMORY_BYTES + 1L,
                                        0,
                                        0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sample(1L, OptionalLong.empty(), 0L, -1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                sample(
                                        1L,
                                        OptionalLong.empty(),
                                        0L,
                                        GraphicsTelemetrySample.MAXIMUM_COUNTER_VALUE + 1,
                                        0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sample(1L, OptionalLong.empty(), 0L, 0, -1));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                sample(
                                        1L,
                                        OptionalLong.empty(),
                                        0L,
                                        0,
                                        GraphicsTelemetrySample.MAXIMUM_COUNTER_VALUE + 1));
    }

    private static GraphicsTelemetrySample sample(
            long cpuFrameTimeNanos,
            OptionalLong gpuFrameTimeNanos,
            long residentMemoryBytes,
            int drawCalls,
            int renderedObjectCount) {
        return new GraphicsTelemetrySample(
                cpuFrameTimeNanos,
                gpuFrameTimeNanos,
                residentMemoryBytes,
                drawCalls,
                renderedObjectCount);
    }
}
