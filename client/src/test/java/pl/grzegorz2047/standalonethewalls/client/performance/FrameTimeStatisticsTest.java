package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrameTimeStatisticsTest {
    @Test
    void summarizesSingleAndOddSampleSetsDeterministically() {
        FrameTimeStatistics single = FrameTimeStatistics.fromNanos(List.of(10_000_000L));
        FrameTimeStatistics odd =
                FrameTimeStatistics.fromNanos(
                        List.of(5_000_000L, 1_000_000L, 4_000_000L, 2_000_000L, 3_000_000L));

        assertThat(single)
                .isEqualTo(
                        new FrameTimeStatistics(
                                1, 10_000_000L, 10_000_000L, 10_000_000L));
        assertThat(odd.medianNanos()).isEqualTo(3_000_000L);
        assertThat(odd.p95Nanos()).isEqualTo(5_000_000L);
        assertThat(odd.p99Nanos()).isEqualTo(5_000_000L);
    }

    @Test
    void usesMidpointMedianAndNearestRankPercentiles() {
        List<Long> samples = new ArrayList<>();
        for (long value = 1L; value <= 100L; value++) {
            samples.add(value * 1_000_000L);
        }

        FrameTimeStatistics statistics = FrameTimeStatistics.fromNanos(samples);

        assertThat(statistics.medianNanos()).isEqualTo(50_500_000L);
        assertThat(statistics.p95Nanos()).isEqualTo(95_000_000L);
        assertThat(statistics.p99Nanos()).isEqualTo(99_000_000L);
    }

    @Test
    void preservesDuplicateSamplesAndDoesNotRetainCallerCollection() {
        List<Long> samples = new ArrayList<>(List.of(4_000_000L, 4_000_000L, 8_000_000L));
        FrameTimeStatistics statistics = FrameTimeStatistics.fromNanos(samples);

        samples.clear();

        assertThat(statistics.sampleCount()).isEqualTo(3);
        assertThat(statistics.medianNanos()).isEqualTo(4_000_000L);
        assertThat(statistics.p95Nanos()).isEqualTo(8_000_000L);
    }

    @Test
    void rejectsEmptyOversizedNullAndOutOfRangeSamples() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FrameTimeStatistics.fromNanos(List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FrameTimeStatistics.fromNanos(
                                        Collections.nCopies(
                                                FrameTimeStatistics.MAXIMUM_SAMPLES + 1,
                                                1_000_000L)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FrameTimeStatistics.fromNanos(
                                        new ArrayList<>(
                                                java.util.Arrays.asList(1_000_000L, null))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FrameTimeStatistics.fromNanos(List.of(0L)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                FrameTimeStatistics.fromNanos(
                                        List.of(FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS + 1L)));
    }
}
