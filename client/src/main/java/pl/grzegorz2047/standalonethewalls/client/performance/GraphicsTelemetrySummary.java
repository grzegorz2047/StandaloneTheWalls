package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic bounded summary for a local renderer telemetry window. */
public record GraphicsTelemetrySummary(
        int sampleCount,
        FrameTimeStatistics cpuFrameTime,
        Optional<FrameTimeStatistics> gpuFrameTime,
        int gpuSampleCount,
        long peakResidentMemoryBytes,
        int peakDrawCalls,
        int peakRenderedObjectCount) {
    public GraphicsTelemetrySummary {
        if (sampleCount < 1 || sampleCount > FrameTimeStatistics.MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException("sampleCount is outside the bounded range");
        }
        cpuFrameTime = Objects.requireNonNull(cpuFrameTime, "cpuFrameTime");
        gpuFrameTime = Objects.requireNonNull(gpuFrameTime, "gpuFrameTime");
        if (cpuFrameTime.sampleCount() != sampleCount) {
            throw new IllegalArgumentException("CPU statistics must cover every sample");
        }
        if (gpuSampleCount < 0 || gpuSampleCount > sampleCount) {
            throw new IllegalArgumentException("gpuSampleCount is outside the bounded range");
        }
        if (gpuSampleCount == 0 && gpuFrameTime.isPresent()) {
            throw new IllegalArgumentException("GPU statistics require available GPU samples");
        }
        if (gpuSampleCount > 0
                && (gpuFrameTime.isEmpty()
                        || gpuFrameTime.orElseThrow().sampleCount() != gpuSampleCount)) {
            throw new IllegalArgumentException("GPU statistics do not match GPU sample coverage");
        }
        if (peakResidentMemoryBytes < 0L
                || peakResidentMemoryBytes
                        > GraphicsTelemetrySample.MAXIMUM_RESIDENT_MEMORY_BYTES) {
            throw new IllegalArgumentException(
                    "peakResidentMemoryBytes is outside the bounded range");
        }
        requirePeakCounter(peakDrawCalls, "peakDrawCalls");
        requirePeakCounter(peakRenderedObjectCount, "peakRenderedObjectCount");
    }

    public static GraphicsTelemetrySummary fromSamples(List<GraphicsTelemetrySample> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty() || samples.size() > FrameTimeStatistics.MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException(
                    "telemetry sample count is outside the bounded range");
        }

        List<Long> cpuFrameTimes = new ArrayList<>(samples.size());
        List<Long> gpuFrameTimes = new ArrayList<>(samples.size());
        long peakResidentMemoryBytes = 0L;
        int peakDrawCalls = 0;
        int peakRenderedObjectCount = 0;

        for (GraphicsTelemetrySample sample : samples) {
            if (sample == null) {
                throw new IllegalArgumentException("telemetry sample cannot be null");
            }
            cpuFrameTimes.add(sample.cpuFrameTimeNanos());
            if (sample.gpuFrameTimeNanos().isPresent()) {
                gpuFrameTimes.add(sample.gpuFrameTimeNanos().getAsLong());
            }
            peakResidentMemoryBytes =
                    Math.max(peakResidentMemoryBytes, sample.residentMemoryBytes());
            peakDrawCalls = Math.max(peakDrawCalls, sample.drawCalls());
            peakRenderedObjectCount =
                    Math.max(peakRenderedObjectCount, sample.renderedObjectCount());
        }

        Optional<FrameTimeStatistics> gpuStatistics =
                gpuFrameTimes.isEmpty()
                        ? Optional.empty()
                        : Optional.of(FrameTimeStatistics.fromNanos(gpuFrameTimes));
        return new GraphicsTelemetrySummary(
                samples.size(),
                FrameTimeStatistics.fromNanos(cpuFrameTimes),
                gpuStatistics,
                gpuFrameTimes.size(),
                peakResidentMemoryBytes,
                peakDrawCalls,
                peakRenderedObjectCount);
    }

    public double gpuCoverageRatio() {
        return (double) gpuSampleCount / sampleCount;
    }

    private static void requirePeakCounter(int value, String fieldName) {
        if (value < 0 || value > GraphicsTelemetrySample.MAXIMUM_COUNTER_VALUE) {
            throw new IllegalArgumentException(fieldName + " is outside the bounded range");
        }
    }
}
