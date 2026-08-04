package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Objects;

/** Axis-aligned closed team region used for preparation spawn and boundary validation. */
public record PreparationRegion(PreparationTeam team, MapVector3 minimum, MapVector3 maximum) {
    public PreparationRegion {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() >= maximum.x()
                || minimum.y() >= maximum.y()
                || minimum.z() >= maximum.z()) {
            throw new IllegalArgumentException(
                    "preparation region minimum must be smaller than maximum on every axis");
        }
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

    public boolean overlapsVolume(PreparationRegion other) {
        PreparationRegion candidate = Objects.requireNonNull(other, "other");
        return minimum.x() < candidate.maximum.x()
                && maximum.x() > candidate.minimum.x()
                && minimum.y() < candidate.maximum.y()
                && maximum.y() > candidate.minimum.y()
                && minimum.z() < candidate.maximum.z()
                && maximum.z() > candidate.minimum.z();
    }
}
