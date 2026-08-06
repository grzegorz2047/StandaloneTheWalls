package pl.grzegorz2047.standalonethewalls.mapformat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One axis-aligned collision box whose top face can support a preparation player. */
public record PreparationSupportBox(String name, MapVector3 minimum, MapVector3 maximum) {
    public static final int MAXIMUM_NAME_BYTES = 64;

    public PreparationSupportBox {
        name = requireName(name);
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() >= maximum.x()
                || minimum.y() >= maximum.y()
                || minimum.z() >= maximum.z()) {
            throw new IllegalArgumentException(
                    "preparation support box minimum must be smaller on every axis");
        }
    }

    public boolean containsHorizontal(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("support query coordinates must be finite");
        }
        return x >= minimum.x()
                && x <= maximum.x()
                && z >= minimum.z()
                && z <= maximum.z();
    }

    public double topYMetres() {
        return maximum.y();
    }

    private static String requireName(String value) {
        String candidate = Objects.requireNonNull(value, "name");
        int bytes = candidate.getBytes(StandardCharsets.US_ASCII).length;
        if (candidate.isEmpty() || bytes > MAXIMUM_NAME_BYTES) {
            throw new IllegalArgumentException("support box name length is outside limits");
        }
        for (int index = 0; index < candidate.length(); index++) {
            char current = candidate.charAt(index);
            if (current < 0x21 || current > 0x7e) {
                throw new IllegalArgumentException("support box name must use visible ASCII");
            }
        }
        return candidate;
    }
}
