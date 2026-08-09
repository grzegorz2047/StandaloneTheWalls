package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/** Bounded atomic persistence for the latest local benchmark report. */
public final class GraphicsBenchmarkReportStore {
    public static final String FILE_NAME = "graphics-benchmark-report.json";
    static final long MAXIMUM_FILE_BYTES = 16_384L;

    private final Path dataDirectory;
    private final Path reportFile;

    public GraphicsBenchmarkReportStore(Path dataDirectory) {
        this.dataDirectory =
                Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.reportFile = this.dataDirectory.resolve(FILE_NAME);
    }

    public Optional<String> load() throws IOException {
        if (!Files.exists(reportFile)) {
            return Optional.empty();
        }
        long size = Files.size(reportFile);
        if (size < 1L || size > MAXIMUM_FILE_BYTES) {
            throw new MalformedReportException(
                    "graphics benchmark report size is outside the bounded range");
        }
        try {
            String report = Files.readString(reportFile, StandardCharsets.UTF_8);
            if (report.isBlank()) {
                throw new MalformedReportException("graphics benchmark report is blank");
            }
            return Optional.of(report);
        } catch (MalformedInputException exception) {
            throw new MalformedReportException(
                    "graphics benchmark report is not valid UTF-8", exception);
        }
    }

    public void save(GraphicsBenchmarkReport report) throws IOException {
        Objects.requireNonNull(report, "report");
        String serialized = GraphicsBenchmarkReportJson.serialize(report);
        byte[] bytes = serialized.getBytes(StandardCharsets.UTF_8);
        if (serialized.isBlank() || bytes.length < 1 || bytes.length > MAXIMUM_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "graphics benchmark report size is outside the bounded range");
        }

        Files.createDirectories(dataDirectory);
        Path temporary = Files.createTempFile(dataDirectory, ".graphics-benchmark-report-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(
                        temporary,
                        reportFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, reportFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path reportFile() {
        return reportFile;
    }

    public static final class MalformedReportException extends IOException {
        private static final long serialVersionUID = 1L;

        public MalformedReportException(String message) {
            super(message);
        }

        public MalformedReportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
