package pl.grzegorz2047.standalonethewalls.client.performance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Stable JSON encoding for local benchmark reports. */
public final class GraphicsBenchmarkReportJson {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private GraphicsBenchmarkReportJson() {}

    public static String serialize(GraphicsBenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        GraphicsBenchmarkResult result = report.result();
        FrameTimeStatistics statistics = result.statistics();
        GraphicsTelemetrySummary telemetry = report.telemetrySummary();

        StringBuilder json = new StringBuilder(768);
        json.append('{');
        appendNumberField(json, "schemaVersion", GraphicsBenchmarkReport.SCHEMA_VERSION);
        appendStringFieldAfterComma(json, "repositoryCommit", report.repositoryCommit());
        json.append(",\"assetPack\":{");
        appendStringField(json, "id", report.assetPackId());
        appendStringFieldAfterComma(json, "version", report.assetPackVersion());
        json.append('}');
        json.append(",\"scenario\":{");
        appendStringField(json, "id", report.scenarioId());
        appendNumberFieldAfterComma(json, "version", report.scenarioVersion());
        json.append('}');
        json.append(",\"measurement\":{");
        appendStringField(json, "measuredPreset", report.measuredPreset().name());
        appendStringFieldAfterComma(json, "recommendedPreset", result.recommendedPreset().name());
        appendStringFieldAfterComma(json, "targetStatus", result.targetStatus().name());
        json.append(",\"resolution\":{");
        appendNumberField(json, "width", result.width());
        appendNumberFieldAfterComma(json, "height", result.height());
        json.append('}');
        json.append(",\"renderScale\":").append(Double.toString(result.renderScale()));
        json.append(",\"frameTimeNanos\":");
        appendStatisticsObject(json, statistics);
        json.append('}');

        json.append(",\"telemetry\":{");
        appendNumberField(json, "sampleCount", telemetry.sampleCount());
        json.append(",\"cpuFrameTimeNanos\":");
        appendStatisticsObject(json, telemetry.cpuFrameTime());
        appendNumberFieldAfterComma(json, "gpuSampleCount", telemetry.gpuSampleCount());
        json.append(",\"gpuFrameTimeNanos\":");
        if (telemetry.gpuFrameTime().isPresent()) {
            appendStatisticsObject(json, telemetry.gpuFrameTime().orElseThrow());
        } else {
            json.append("null");
        }
        appendNumberFieldAfterComma(
                json, "peakResidentMemoryBytes", telemetry.peakResidentMemoryBytes());
        appendNumberFieldAfterComma(json, "peakDrawCalls", telemetry.peakDrawCalls());
        appendNumberFieldAfterComma(
                json, "peakRenderedObjectCount", telemetry.peakRenderedObjectCount());
        json.append("}}\n");
        return json.toString();
    }

    public static byte[] serializeUtf8(GraphicsBenchmarkReport report) {
        return serialize(report).getBytes(StandardCharsets.UTF_8);
    }

    private static void appendStatisticsObject(StringBuilder json, FrameTimeStatistics statistics) {
        json.append('{');
        appendNumberField(json, "sampleCount", statistics.sampleCount());
        appendNumberFieldAfterComma(json, "median", statistics.medianNanos());
        appendNumberFieldAfterComma(json, "p95", statistics.p95Nanos());
        appendNumberFieldAfterComma(json, "p99", statistics.p99Nanos());
        json.append('}');
    }

    private static void appendStringField(StringBuilder json, String name, String value) {
        appendQuoted(json, name);
        json.append(':');
        appendQuoted(json, value);
    }

    private static void appendStringFieldAfterComma(StringBuilder json, String name, String value) {
        json.append(',');
        appendStringField(json, name, value);
    }

    private static void appendNumberField(StringBuilder json, String name, long value) {
        appendQuoted(json, name);
        json.append(':').append(value);
    }

    private static void appendNumberFieldAfterComma(StringBuilder json, String name, long value) {
        json.append(',');
        appendNumberField(json, name, value);
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u00");
                        json.append(HEX[(character >>> 4) & 0x0F]);
                        json.append(HEX[character & 0x0F]);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
