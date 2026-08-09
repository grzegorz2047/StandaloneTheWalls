package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkReportJsonTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void serializesStableSchemaInFixedFieldOrderWithoutGpuSamples() {
        GraphicsBenchmarkReport report = report("core-pack", "v1", "reference-scene");

        String first = GraphicsBenchmarkReportJson.serialize(report);
        String second = GraphicsBenchmarkReportJson.serialize(report);

        assertThat(second).isEqualTo(first);
        assertThat(first)
                .isEqualTo(
                        "{\"schemaVersion\":2,\"repositoryCommit\":\"0123456789abcdef0123456789abcdef01234567\","
                                + "\"assetPack\":{\"id\":\"core-pack\",\"version\":\"v1\"},"
                                + "\"scenario\":{\"id\":\"reference-scene\",\"version\":2},"
                                + "\"measurement\":{\"measuredPreset\":\"MEDIUM\","
                                + "\"recommendedPreset\":\"MEDIUM\","
                                + "\"targetStatus\":\"MEETS_PRIMARY_TARGET\","
                                + "\"resolution\":{\"width\":1920,\"height\":1080},"
                                + "\"renderScale\":0.75,\"frameTimeNanos\":{\"sampleCount\":120,"
                                + "\"median\":12000000,\"p95\":16700000,\"p99\":17100000}},"
                                + "\"telemetry\":{\"sampleCount\":120,\"cpuFrameTimeNanos\":{"
                                + "\"sampleCount\":120,\"median\":12000000,\"p95\":16700000,"
                                + "\"p99\":17100000},\"gpuSampleCount\":0,"
                                + "\"gpuFrameTimeNanos\":null,\"peakResidentMemoryBytes\":512000000,"
                                + "\"peakDrawCalls\":321,\"peakRenderedObjectCount\":654}}\n");
    }

    @Test
    void serializesPartialGpuCoverageWithoutInventingMissingSamples() {
        FrameTimeStatistics cpu = benchmarkStatistics();
        FrameTimeStatistics gpu = new FrameTimeStatistics(2, 13_000_000L, 14_000_000L, 14_000_000L);
        GraphicsTelemetrySummary telemetry =
                new GraphicsTelemetrySummary(120, cpu, Optional.of(gpu), 2, 700_000_000L, 444, 555);
        GraphicsBenchmarkReport report = report("core-pack", "v2", "reference-scene", telemetry);

        assertThat(GraphicsBenchmarkReportJson.serialize(report))
                .isEqualTo(
                        "{\"schemaVersion\":2,\"repositoryCommit\":\"0123456789abcdef0123456789abcdef01234567\","
                                + "\"assetPack\":{\"id\":\"core-pack\",\"version\":\"v2\"},"
                                + "\"scenario\":{\"id\":\"reference-scene\",\"version\":2},"
                                + "\"measurement\":{\"measuredPreset\":\"MEDIUM\","
                                + "\"recommendedPreset\":\"MEDIUM\","
                                + "\"targetStatus\":\"MEETS_PRIMARY_TARGET\","
                                + "\"resolution\":{\"width\":1920,\"height\":1080},"
                                + "\"renderScale\":0.75,\"frameTimeNanos\":{\"sampleCount\":120,"
                                + "\"median\":12000000,\"p95\":16700000,\"p99\":17100000}},"
                                + "\"telemetry\":{\"sampleCount\":120,\"cpuFrameTimeNanos\":{"
                                + "\"sampleCount\":120,\"median\":12000000,\"p95\":16700000,"
                                + "\"p99\":17100000},\"gpuSampleCount\":2,"
                                + "\"gpuFrameTimeNanos\":{\"sampleCount\":2,\"median\":13000000,"
                                + "\"p95\":14000000,\"p99\":14000000},"
                                + "\"peakResidentMemoryBytes\":700000000,\"peakDrawCalls\":444,"
                                + "\"peakRenderedObjectCount\":555}}\n");
    }

    @Test
    void escapesStringsAndProducesUtf8Bytes() {
        GraphicsBenchmarkReport report = report("core \"pack\"\\desktop", "wersja-ą", "scene/β");

        String json = GraphicsBenchmarkReportJson.serialize(report);
        byte[] utf8 = GraphicsBenchmarkReportJson.serializeUtf8(report);

        assertThat(json).contains("\"id\":\"core \\\"pack\\\"\\\\desktop\"");
        assertThat(json).contains("\"version\":\"wersja-ą\"");
        assertThat(json).contains("\"id\":\"scene/β\"");
        assertThat(utf8).containsExactly(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void renderScaleEncodingDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);

            String json = GraphicsBenchmarkReportJson.serialize(report("assets", "1", "scenario"));

            assertThat(json).contains("\"renderScale\":0.75");
            assertThat(json).doesNotContain("0,75");
        } finally {
            Locale.setDefault(original);
        }
    }

    private static GraphicsBenchmarkReport report(
            String assetPackId, String assetPackVersion, String scenarioId) {
        return report(assetPackId, assetPackVersion, scenarioId, telemetrySummary());
    }

    private static GraphicsBenchmarkReport report(
            String assetPackId,
            String assetPackVersion,
            String scenarioId,
            GraphicsTelemetrySummary telemetry) {
        return new GraphicsBenchmarkReport(
                COMMIT,
                assetPackId,
                assetPackVersion,
                scenarioId,
                2,
                GraphicsQualityPreset.MEDIUM,
                new GraphicsBenchmarkResult(
                        GraphicsQualityPreset.MEDIUM,
                        GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET,
                        benchmarkStatistics(),
                        1920,
                        1080,
                        0.75d),
                telemetry);
    }

    private static GraphicsTelemetrySummary telemetrySummary() {
        return new GraphicsTelemetrySummary(
                120, benchmarkStatistics(), Optional.empty(), 0, 512_000_000L, 321, 654);
    }

    private static FrameTimeStatistics benchmarkStatistics() {
        return new FrameTimeStatistics(120, 12_000_000L, 16_700_000L, 17_100_000L);
    }
}
