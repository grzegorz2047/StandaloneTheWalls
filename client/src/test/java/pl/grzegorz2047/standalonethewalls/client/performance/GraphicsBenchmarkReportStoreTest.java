package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkReportStoreTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @TempDir Path tempDirectory;

    @Test
    void missingReportReadsAsEmpty() throws IOException {
        assertThat(new GraphicsBenchmarkReportStore(tempDirectory).load()).isEmpty();
    }

    @Test
    void deterministicReportRoundTripsExactly() throws IOException {
        GraphicsBenchmarkReportStore store = new GraphicsBenchmarkReportStore(tempDirectory);
        GraphicsBenchmarkReport report = report();
        String expected = GraphicsBenchmarkReportJson.serialize(report);

        store.save(report);

        assertThat(store.load()).contains(expected);
        assertThat(Files.readString(store.reportFile(), StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void blankOversizedAndInvalidUtf8ReportsAreRejected() throws IOException {
        GraphicsBenchmarkReportStore store = new GraphicsBenchmarkReportStore(tempDirectory);
        Path reportFile = store.reportFile();

        assertThat(Files.writeString(reportFile, "   \n", StandardCharsets.UTF_8))
                .isEqualTo(reportFile);
        assertThatThrownBy(store::load)
                .isInstanceOf(GraphicsBenchmarkReportStore.MalformedReportException.class);

        byte[] oversized = new byte[(int) GraphicsBenchmarkReportStore.MAXIMUM_FILE_BYTES + 1];
        assertThat(Files.write(reportFile, oversized)).isEqualTo(reportFile);
        assertThatThrownBy(store::load)
                .isInstanceOf(GraphicsBenchmarkReportStore.MalformedReportException.class);

        assertThat(Files.write(reportFile, new byte[] {(byte) 0xC3, 0x28}))
                .isEqualTo(reportFile);
        assertThatThrownBy(store::load)
                .isInstanceOf(GraphicsBenchmarkReportStore.MalformedReportException.class);
    }

    private static GraphicsBenchmarkReport report() {
        return new GraphicsBenchmarkReport(
                COMMIT,
                "core",
                "9",
                "reference",
                3,
                GraphicsQualityPreset.MEDIUM,
                new GraphicsBenchmarkResult(
                        GraphicsQualityPreset.MEDIUM,
                        GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET,
                        new FrameTimeStatistics(3, 10_000_000L, 12_000_000L, 12_000_000L),
                        1920,
                        1080,
                        1.0d));
    }
}
