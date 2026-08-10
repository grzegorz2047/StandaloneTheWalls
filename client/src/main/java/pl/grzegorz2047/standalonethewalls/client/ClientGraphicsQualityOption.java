package pl.grzegorz2047.standalonethewalls.client;

import java.util.Locale;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsQualityPreset;

/** User-facing launch-time graphics quality preference mutation. */
public enum ClientGraphicsQualityOption {
    UNCHANGED,
    AUTO,
    LOW,
    MEDIUM,
    HIGH;

    static ClientGraphicsQualityOption parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "--graphics-preset must be auto, low, medium, or high", exception);
        }
    }

    public boolean changesPersistedState() {
        return this != UNCHANGED;
    }

    public Optional<GraphicsQualityPreset> manualOverride() {
        return switch (this) {
            case UNCHANGED -> throw new IllegalStateException(
                    "unchanged graphics option has no override mutation");
            case AUTO -> Optional.empty();
            case LOW -> Optional.of(GraphicsQualityPreset.LOW);
            case MEDIUM -> Optional.of(GraphicsQualityPreset.MEDIUM);
            case HIGH -> Optional.of(GraphicsQualityPreset.HIGH);
        };
    }
}
