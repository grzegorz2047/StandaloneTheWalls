package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Bounded rolling local telemetry window retaining the most recent frame samples. */
public final class GraphicsTelemetryWindow {
    private final ArrayDeque<GraphicsTelemetrySample> samples = new ArrayDeque<>();

    public void add(GraphicsTelemetrySample sample) {
        Objects.requireNonNull(sample, "sample");
        if (samples.size() == FrameTimeStatistics.MAXIMUM_SAMPLES) {
            samples.removeFirst();
        }
        samples.addLast(sample);
    }

    public int sampleCount() {
        return samples.size();
    }

    public Optional<GraphicsTelemetrySummary> summary() {
        if (samples.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GraphicsTelemetrySummary.fromSamples(new ArrayList<>(samples)));
    }
}
