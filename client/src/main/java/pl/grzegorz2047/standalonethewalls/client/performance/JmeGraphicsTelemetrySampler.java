package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.renderer.Renderer;
import com.jme3.renderer.Statistics;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** Samples the previous rendered jME frame into the local telemetry contract. */
public final class JmeGraphicsTelemetrySampler implements AutoCloseable {
    private static final String OBJECTS_LABEL = "Objects";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Statistics statistics;
    private final LongSupplier usedMemoryBytes;
    private final int objectsIndex;
    private final boolean statisticsInitiallyEnabled;
    private boolean previousRenderAvailable;
    private boolean closed;

    public static JmeGraphicsTelemetrySampler forRenderer(
            Renderer renderer, LongSupplier usedMemoryBytes) {
        Objects.requireNonNull(renderer, "renderer");
        return new JmeGraphicsTelemetrySampler(renderer.getStatistics(), usedMemoryBytes);
    }

    JmeGraphicsTelemetrySampler(Statistics statistics, LongSupplier usedMemoryBytes) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.usedMemoryBytes = Objects.requireNonNull(usedMemoryBytes, "usedMemoryBytes");
        this.objectsIndex = findObjectsIndex(statistics.getLabels());
        this.statisticsInitiallyEnabled = statistics.isEnabled();
        statistics.setEnabled(true);
    }

    public Optional<GraphicsTelemetrySample> sample(float timePerFrame, Node... sceneRoots) {
        if (closed) {
            throw new IllegalStateException("telemetry sampler is closed");
        }
        Objects.requireNonNull(sceneRoots, "sceneRoots");
        long cpuFrameTimeNanos = toFrameTimeNanos(timePerFrame);
        for (Node root : sceneRoots) {
            Objects.requireNonNull(root, "scene root");
        }
        if (!previousRenderAvailable) {
            previousRenderAvailable = true;
            return Optional.empty();
        }

        String[] labels = statistics.getLabels();
        int[] data = new int[labels.length];
        statistics.getData(data);
        int drawCalls = data[objectsIndex];
        int renderedObjectCount = 0;
        for (Node root : sceneRoots) {
            renderedObjectCount = Math.addExact(renderedObjectCount, countGeometries(root));
        }

        return Optional.of(
                new GraphicsTelemetrySample(
                        cpuFrameTimeNanos,
                        OptionalLong.empty(),
                        usedMemoryBytes.getAsLong(),
                        drawCalls,
                        renderedObjectCount));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        statistics.setEnabled(statisticsInitiallyEnabled);
    }

    private static int findObjectsIndex(String[] labels) {
        Objects.requireNonNull(labels, "labels");
        for (int index = 0; index < labels.length; index++) {
            if (OBJECTS_LABEL.equals(labels[index])) {
                return index;
            }
        }
        throw new IllegalArgumentException("jME renderer statistics do not expose Objects");
    }

    private static long toFrameTimeNanos(float timePerFrame) {
        if (!Float.isFinite(timePerFrame) || timePerFrame <= 0f) {
            throw new IllegalArgumentException("timePerFrame must be finite and positive");
        }
        long nanos = Math.round((double) timePerFrame * NANOS_PER_SECOND);
        if (nanos <= 0L || nanos > FrameTimeStatistics.MAXIMUM_SAMPLE_NANOS) {
            throw new IllegalArgumentException("timePerFrame is outside the bounded range");
        }
        return nanos;
    }

    private static int countGeometries(Spatial spatial) {
        if (spatial instanceof Geometry) {
            return 1;
        }
        if (!(spatial instanceof Node node)) {
            return 0;
        }
        int count = 0;
        for (Spatial child : node.getChildren()) {
            count = Math.addExact(count, countGeometries(child));
            if (count > GraphicsTelemetrySample.MAXIMUM_COUNTER_VALUE) {
                throw new IllegalArgumentException("scene object count is outside the bounded range");
            }
        }
        return count;
    }
}
