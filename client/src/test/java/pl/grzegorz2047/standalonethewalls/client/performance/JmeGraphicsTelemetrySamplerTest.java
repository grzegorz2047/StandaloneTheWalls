package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.jme3.renderer.Statistics;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JmeGraphicsTelemetrySamplerTest {
    @Test
    void warmsUpThenSamplesPreviousRendererFrameAndCurrentGeometryCount() {
        FixedStatistics statistics = new FixedStatistics(7);
        Node root = new Node("root");
        root.attachChild(new Geometry("first", new Mesh()));
        Node nested = new Node("nested");
        nested.attachChild(new Geometry("second", new Mesh()));
        root.attachChild(nested);

        try (JmeGraphicsTelemetrySampler sampler =
                new JmeGraphicsTelemetrySampler(statistics, () -> 2_048L)) {
            assertThat(statistics.isEnabled()).isTrue();
            assertThat(sampler.sample(0.125f, root)).isEmpty();

            GraphicsTelemetrySample sample = sampler.sample(0.125f, root).orElseThrow();

            assertThat(sample.cpuFrameTimeNanos()).isEqualTo(125_000_000L);
            assertThat(sample.gpuFrameTimeNanos()).isEmpty();
            assertThat(sample.residentMemoryBytes()).isEqualTo(2_048L);
            assertThat(sample.drawCalls()).isEqualTo(7);
            assertThat(sample.renderedObjectCount()).isEqualTo(2);
        }

        assertThat(statistics.isEnabled()).isFalse();
    }

    @Test
    void pollsGpuTimingOnlyForFramesThatHaveAPreviousRender() {
        FixedStatistics statistics = new FixedStatistics(3);
        AtomicInteger gpuPolls = new AtomicInteger();
        GpuFrameTimeSource gpuFrameTimeSource =
                () -> {
                    gpuPolls.incrementAndGet();
                    return OptionalLong.of(8_000_000L);
                };

        try (JmeGraphicsTelemetrySampler sampler =
                new JmeGraphicsTelemetrySampler(statistics, () -> 1_024L, gpuFrameTimeSource)) {
            assertThat(sampler.sample(0.016f, new Node())).isEmpty();
            assertThat(gpuPolls).hasValue(0);

            GraphicsTelemetrySample sample = sampler.sample(0.016f, new Node()).orElseThrow();

            assertThat(gpuPolls).hasValue(1);
            assertThat(sample.gpuFrameTimeNanos()).hasValue(8_000_000L);
        }
    }

    @Test
    void restoresPreviouslyEnabledStatisticsAndCloseIsIdempotent() {
        FixedStatistics statistics = new FixedStatistics(1);
        statistics.setEnabled(true);
        JmeGraphicsTelemetrySampler sampler = new JmeGraphicsTelemetrySampler(statistics, () -> 0L);

        sampler.close();
        sampler.close();

        assertThat(statistics.isEnabled()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> sampler.sample(0.125f, new Node()));
    }

    @Test
    void rejectsInvalidFrameTimeAndMissingObjectsStatistic() {
        FixedStatistics statistics = new FixedStatistics(1);
        try (JmeGraphicsTelemetrySampler sampler =
                new JmeGraphicsTelemetrySampler(statistics, () -> 0L)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> sampler.sample(Float.NaN, new Node()));
            assertThatIllegalArgumentException().isThrownBy(() -> sampler.sample(0f, new Node()));
        }

        Statistics missingObjects =
                new Statistics() {
                    @Override
                    public String[] getLabels() {
                        return new String[] {"Triangles"};
                    }
                };
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JmeGraphicsTelemetrySampler(missingObjects, () -> 0L));
    }

    private static final class FixedStatistics extends Statistics {
        private final int objects;

        private FixedStatistics(int objects) {
            this.objects = objects;
        }

        @Override
        public String[] getLabels() {
            return new String[] {"Objects"};
        }

        @Override
        public void getData(int[] data) {
            data[0] = objects;
        }
    }
}
