package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class GraphicsBenchmarkReportTest {
    private static final String COMMIT = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";

    @Test
    void normalizesCommitAndRetainsReproducibilityMetadata() {
        GraphicsBenchmarkReport report =
                new GraphicsBenchmarkReport(
                        COMMIT,
                        "assets.lock.json",
                        "schema-1",
                        "integrated-gpu-reference",
                        3,
                        GraphicsQualityPreset.MEDIUM,
                        benchmarkResult());

        assertThat(report.repositoryCommit()).isEqualTo(COMMIT.toLowerCase(Locale.ROOT));
        assertThat(report.assetPackId()).isEqualTo("assets.lock.json");
        assertThat(report.assetPackVersion()).isEqualTo("schema-1");
        assertThat(report.scenarioId()).isEqualTo("integrated-gpu-reference");
        assertThat(report.scenarioVersion()).isEqualTo(3);
        assertThat(report.measuredPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(report.result()).isEqualTo(benchmarkResult());
    }

    @Test
    void rejectsMalformedRepositoryCommit() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkReport(
                                        "abc123",
                                        "assets",
                                        "1",
                                        "scenario",
                                        1,
                                        GraphicsQualityPreset.LOW,
                                        benchmarkResult()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new GraphicsBenchmarkReport(
                                        "z".repeat(40),
                                        "assets",
                                        "1",
                                        "scenario",
                                        1,
                                        GraphicsQualityPreset.LOW,
                                        benchmarkResult()));
    }

    @Test
    void rejectsUnboundedControlAndMalformedMetadata() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                reportWithMetadata(
                                        "x"
                                                .repeat(
                                                        GraphicsBenchmarkReport
                                                                        .MAXIMUM_METADATA_LENGTH
                                                                + 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> reportWithMetadata("line\nbreak"));
        assertThatIllegalArgumentException().isThrownBy(() -> reportWithMetadata("\uD800"));
        assertThatIllegalArgumentException().isThrownBy(() -> reportWithMetadata("   "));
    }

    @Test
    void rejectsScenarioVersionsOutsideBoundedRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> reportWithScenarioVersion(0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                reportWithScenarioVersion(
                                        GraphicsBenchmarkReport.MAXIMUM_SCENARIO_VERSION + 1));
    }

    private static GraphicsBenchmarkReport reportWithMetadata(String metadata) {
        return report(metadata, 1);
    }

    private static GraphicsBenchmarkReport reportWithScenarioVersion(int scenarioVersion) {
        return report("assets", scenarioVersion);
    }

    private static GraphicsBenchmarkReport report(String metadata, int scenarioVersion) {
        GraphicsQualityPreset preset = GraphicsQualityPreset.LOW;
        return new GraphicsBenchmarkReport(
                COMMIT, metadata, "1", "scenario", scenarioVersion, preset, benchmarkResult());
    }

    private static GraphicsBenchmarkResult benchmarkResult() {
        return new GraphicsBenchmarkResult(
                GraphicsQualityPreset.MEDIUM,
                GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET,
                new FrameTimeStatistics(120, 12_000_000L, 16_700_000L, 17_000_000L),
                1920,
                1080,
                1.0d);
    }
}
