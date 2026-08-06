package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.List;
import java.util.Objects;

/** Axis-aligned closed bounds covering every verified preparation team region. */
public record PreparationWorldBounds(MapVector3 minimum, MapVector3 maximum) {
    public PreparationWorldBounds {
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() >= maximum.x()
                || minimum.y() >= maximum.y()
                || minimum.z() >= maximum.z()) {
            throw new IllegalArgumentException(
                    "preparation world minimum must be smaller than maximum on every axis");
        }
    }

    public static PreparationWorldBounds fromRegions(List<PreparationRegion> regions) {
        List<PreparationRegion> verified =
                List.copyOf(Objects.requireNonNull(regions, "regions"));
        if (verified.isEmpty()) {
            throw new IllegalArgumentException("preparation world requires at least one region");
        }
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (PreparationRegion region : verified) {
            PreparationRegion candidate = Objects.requireNonNull(region, "region");
            minimumX = Math.min(minimumX, candidate.minimum().x());
            minimumY = Math.min(minimumY, candidate.minimum().y());
            minimumZ = Math.min(minimumZ, candidate.minimum().z());
            maximumX = Math.max(maximumX, candidate.maximum().x());
            maximumY = Math.max(maximumY, candidate.maximum().y());
            maximumZ = Math.max(maximumZ, candidate.maximum().z());
        }
        return new PreparationWorldBounds(
                new MapVector3(minimumX, minimumY, minimumZ),
                new MapVector3(maximumX, maximumY, maximumZ));
    }

    public boolean contains(MapVector3 point) {
        MapVector3 candidate = Objects.requireNonNull(point, "point");
        return candidate.x() >= minimum.x()
                && candidate.x() <= maximum.x()
                && candidate.y() >= minimum.y()
                && candidate.y() <= maximum.y()
                && candidate.z() >= minimum.z()
                && candidate.z() <= maximum.z();
    }

    public double clampX(double value) {
        return clamp(value, minimum.x(), maximum.x());
    }

    public double clampZ(double value) {
        return clamp(value, minimum.z(), maximum.z());
    }

    private static double clamp(double value, double minimumValue, double maximumValue) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("preparation world coordinate must be finite");
        }
        return Math.max(minimumValue, Math.min(maximumValue, value));
    }
}
