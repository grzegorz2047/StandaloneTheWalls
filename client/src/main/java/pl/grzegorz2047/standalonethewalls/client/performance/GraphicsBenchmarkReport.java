package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Locale;
import java.util.Objects;

/** Reproducibility metadata and measured result for one local graphics benchmark run. */
public record GraphicsBenchmarkReport(
        String repositoryCommit,
        String assetPackId,
        String assetPackVersion,
        String scenarioId,
        int scenarioVersion,
        GraphicsQualityPreset measuredPreset,
        GraphicsBenchmarkResult result) {
    public static final int SCHEMA_VERSION = 1;
    static final int MAXIMUM_METADATA_LENGTH = 160;
    static final int MAXIMUM_SCENARIO_VERSION = 1_000_000;

    public GraphicsBenchmarkReport {
        repositoryCommit = normalizeCommit(repositoryCommit);
        assetPackId = requireBoundedMetadata(assetPackId, "assetPackId");
        assetPackVersion = requireBoundedMetadata(assetPackVersion, "assetPackVersion");
        scenarioId = requireBoundedMetadata(scenarioId, "scenarioId");
        if (scenarioVersion < 1 || scenarioVersion > MAXIMUM_SCENARIO_VERSION) {
            throw new IllegalArgumentException("scenarioVersion is outside the bounded range");
        }
        Objects.requireNonNull(measuredPreset, "measuredPreset");
        Objects.requireNonNull(result, "result");
    }

    private static String normalizeCommit(String repositoryCommit) {
        Objects.requireNonNull(repositoryCommit, "repositoryCommit");
        int length = repositoryCommit.length();
        if (length != 40 && length != 64) {
            throw new IllegalArgumentException("repositoryCommit must be a full Git object id");
        }
        for (int index = 0; index < length; index++) {
            char character = repositoryCommit.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lowerHex = character >= 'a' && character <= 'f';
            boolean upperHex = character >= 'A' && character <= 'F';
            if (!digit && !lowerHex && !upperHex) {
                throw new IllegalArgumentException("repositoryCommit must be hexadecimal");
            }
        }
        return repositoryCommit.toLowerCase(Locale.ROOT);
    }

    private static String requireBoundedMetadata(String value, String fieldName) {
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
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(fieldName + " contains an unpaired surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(fieldName + " contains an unpaired surrogate");
            }
        }
        return value;
    }
}
