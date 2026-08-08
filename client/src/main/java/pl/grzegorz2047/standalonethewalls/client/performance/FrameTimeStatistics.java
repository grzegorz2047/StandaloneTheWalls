package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded frame-time summary using nearest-rank p95/p99 percentiles. */
public record FrameTimeStatistics(
        int sampleCount, long medianNanos, long p95Nanos, long p99Nanos) {
    public static final int MAXIMUM_SAMPLES = 4_096;
    public static final long MAXIMUM_SAMPLE_NANOS = 10_000_000_000L;

    public FrameTimeStatistics {
        if (sampleCount < 1 || sampleCount > MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException("sampleCount is outside the bounded range");
        }
        requireFrameTime(medianNanos, "medianNanos");
        requireFrameTime(p95Nanos, "p95Nanos");
        requireFrameTime(p99Nanos, "p99Nanos");
        if (medianNanos > p95Nanos || p95Nanos > p99Nanos) {
            throw new IllegalArgumentException("frame-time statistics must be monotonic");
        }
    }

    public static FrameTimeStatistics fromNanos(List<Long> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty() || samples.size() > MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException("frame-time sample count is outside the bounded range");
        }

        long[] sorted = new long[samples.size()];
        for (int index = 0; index < samples.size(); index++) {
            Long sample = samples.get(index);
            if (sample == null) {
                throw new IllegalArgumentException("frame-time sample cannot be null");
            }
            requireFrameTime(sample, "frame-time sample");
            sorted[index] = sample;
        }
        Arrays.sort(sorted);

        return new FrameTimeStatistics(
                sorted.length,
                median(sorted),
                nearestRank(sorted, 95),
                nearestRank(sorted, 99));
    }

    public double medianMilliseconds() {
        return nanosToMilliseconds(medianNanos);
    }

    public double p95Milliseconds() {
        return nanosToMilliseconds(p95Nanos);
    }

    public double p99Milliseconds() {
        return nanosToMilliseconds(p99Nanos);
    }

    private static long median(long[] sorted) {
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) {
            return sorted[middle];
        }
        long lower = sorted[middle - 1];
        long upper = sorted[middle];
        return lower + (upper - lower) / 2L;
    }

    private static long nearestRank(long[] sorted, int percentile) {
        long rank = ((long) percentile * sorted.length + 99L) / 100L;
        return sorted[(int) rank - 1];
    }

    private static void requireFrameTime(long value, String field) {
        if (value <= 0L || value > MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
    }

    private static double nanosToMilliseconds(long value) {
        return value / 1_000_000.0d;
    }
}
