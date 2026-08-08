package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkReportJsonTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void serializesStableSchemaInFixedFieldOrder() {
        GraphicsBenchmarkReport report = report("core-pack", "v1", "reference-scene");

        String first = GraphicsBenchmarkReportJson.serialize(report);
        String second = GraphicsBenchmarkReportJson.serialize(report);

        assertThat(second).isEqualTo(first);
        assertThat(first)
                .isEqualTo(
                        "{\"schemaVersion\":1,\"repositoryCommit\":\"0123456789abcdef0123456789abcdef01234567\","
                                + "\"assetPack\":{\"id\":\"core-pack\",\"version\":\"v1\"},"
                                + "\"scenario\":{\"id\":\"reference-scene\",\"version\":2},"
                                + "\"measurement\":{\"measuredPreset\":\"MEDIUM\","
                                + "\"recommendedPreset\":\"MEDIUM\","
                                + "\"targetStatus\":\"MEETS_PRIMARY_TARGET\","
                                + "\"resolution\":{\"width\":1920,\"height\":1080},"
                                + "\"renderScale\":0.75,\"frameTimeNanos\":{\"sampleCount\":120,"
                                + "\"median\":12000000,\"p95\":16700000,\"p99\":17100000}}}\n");
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
                        new FrameTimeStatistics(
                                120, 12_000_000L, 16_700_000L, 17_100_000L),
                        1920,
                        1080,
                        0.75d));
    }
}
