package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;
import java.util.OptionalLong;

/** One local renderer telemetry sample with optional GPU timing coverage. */
public record GraphicsTelemetrySample(
        long cpuFrameTimeNanos,
        OptionalLong gpuFrameTimeNanos,
        long residentMemoryBytes,
        int drawCalls,
        int renderedObjectCount) {
    public static final long MAXIMUM_RESIDENT_MEMORY_BYTES = 1L << 50;
    public static final int MAXIMUM_COUNTER_VALUE = 10_000_000;

    public GraphicsTelemetrySample {
        gpuFrameTimeNanos = Objects.requireNonNull(gpuFrameTimeNanos, "gpuFrameTimeNanos");
        requireFrameTime(cpuFrameTimeNanos, "cpuFrameTimeNanos");
        if (gpuFrameTimeNanos.isPresent()) {
            requireFrameTime(gpuFrameTimeNanos.getAsLong(), "gpuFrameTimeNanos");
        }
        if (residentMemoryBytes < 0L || residentMemoryBytes > MAXIMUM_RESIDENT_MEMORY_BYTES) {
            throw new IllegalArgumentException("residentMemoryBytes is outside the bounded range");
        }
        requireCounter(drawCalls, "drawCalls");
        requireCounter(renderedObjectCount, "renderedObjectCount");
    }

    private static void requireFrameTime(long value, String fieldName) {
        if (value <= 0L || value > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException(fieldName + " is outside the bounded range");
        }
    }

    private static void requireCounter(int value, String fieldName) {
        if (value < 0 || value > MAXIMUM_COUNTER_VALUE) {
            throw new IllegalArgumentException(fieldName + " is outside the bounded range");
        }
    }
}
