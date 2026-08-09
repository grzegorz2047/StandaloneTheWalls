package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GraphicsTelemetryWindowTest {
    @Test
    void staysBoundedAndDropsTheOldestSample() {
        GraphicsTelemetryWindow window = new GraphicsTelemetryWindow();
        window.add(sample(1_000L, 999L));
        for (int index = 0; index < FrameTimeStatistics.MAXIMUM_SAMPLES; index++) {
            window.add(sample(2_000L + index, 100L));
        }

        GraphicsTelemetrySummary summary = window.summary().orElseThrow();

        assertThat(window.sampleCount()).isEqualTo(FrameTimeStatistics.MAXIMUM_SAMPLES);
        assertThat(summary.sampleCount()).isEqualTo(FrameTimeStatistics.MAXIMUM_SAMPLES);
        assertThat(summary.peakResidentMemoryBytes()).isEqualTo(100L);
    }

    @Test
    void emptyWindowHasNoSummaryAndNullSamplesAreRejected() {
        GraphicsTelemetryWindow window = new GraphicsTelemetryWindow();

        assertThat(window.summary()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> window.add(null));
    }

    private static GraphicsTelemetrySample sample(long cpuFrameTimeNanos, long memoryBytes) {
        return new GraphicsTelemetrySample(
                cpuFrameTimeNanos, OptionalLong.empty(), memoryBytes, 1, 1);
    }
}
