package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/** Asset/scenario identity that determines whether a persisted benchmark is still reusable. */
public record GraphicsBenchmarkCompatibilityKey(
        String assetPackId, String assetPackVersion, String scenarioId, int scenarioVersion) {
    private static final int MAXIMUM_METADATA_LENGTH = 160;
    private static final int MAXIMUM_SCENARIO_VERSION = 1_000_000;

    public GraphicsBenchmarkCompatibilityKey {
        assetPackId = requireMetadata(assetPackId, "assetPackId");
        assetPackVersion = requireMetadata(assetPackVersion, "assetPackVersion");
        scenarioId = requireMetadata(scenarioId, "scenarioId");
        if (scenarioVersion < 1 || scenarioVersion > MAXIMUM_SCENARIO_VERSION) {
            throw new IllegalArgumentException("scenarioVersion is outside the bounded range");
        }
    }

    public static GraphicsBenchmarkCompatibilityKey fromReport(GraphicsBenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        return new GraphicsBenchmarkCompatibilityKey(
                report.assetPackId(),
                report.assetPackVersion(),
                report.scenarioId(),
                report.scenarioVersion());
    }

    private static String requireMetadata(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank() || value.length() > MAXIMUM_METADATA_LENGTH) {
            throw new IllegalArgumentException(fieldName + " is outside the bounded range");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException(fieldName + " contains a control character");
            }
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            fieldName + " contains an unpaired surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(fieldName + " contains an unpaired surrogate");
            }
        }
        return value;
    }
}
