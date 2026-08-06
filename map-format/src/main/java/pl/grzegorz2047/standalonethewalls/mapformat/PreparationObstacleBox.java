package pl.grzegorz2047.standalonethewalls.mapformat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One axis-aligned collision box that blocks preparation player movement. */
public record PreparationObstacleBox(String name, MapVector3 minimum, MapVector3 maximum) {
    public static final int MAXIMUM_NAME_BYTES = 64;

    public PreparationObstacleBox {
        name = requireName(name);
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() >= maximum.x()
                || minimum.y() >= maximum.y()
                || minimum.z() >= maximum.z()) {
            throw new IllegalArgumentException(
                    "preparation obstacle box minimum must be smaller on every axis");
        }
    }

    private static String requireName(String value) {
        String candidate = Objects.requireNonNull(value, "name");
        int bytes = candidate.getBytes(StandardCharsets.US_ASCII).length;
        if (candidate.isEmpty() || bytes > MAXIMUM_NAME_BYTES) {
            throw new IllegalArgumentException("obstacle box name length is outside limits");
        }
        for (int index = 0; index < candidate.length(); index++) {
            char current = candidate.charAt(index);
            if (current < 0x21 || current > 0x7e) {
                throw new IllegalArgumentException("obstacle box name must use visible ASCII");
            }
        }
        return candidate;
    }
}
