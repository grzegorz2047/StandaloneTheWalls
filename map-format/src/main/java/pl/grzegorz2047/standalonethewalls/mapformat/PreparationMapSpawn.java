package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Objects;

/** One exclusive preparation spawn declared by gameplay metadata. */
public record PreparationMapSpawn(
        int index, PreparationTeam team, MapVector3 position, double yawDegrees) {
    public static final int MAXIMUM_INDEX = 4_095;

    public PreparationMapSpawn {
        if (index < 0 || index > MAXIMUM_INDEX) {
            throw new IllegalArgumentException("preparation spawn index is outside the supported range");
        }
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(yawDegrees) || yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw new IllegalArgumentException("preparation spawn yaw must be finite and in [-180, 180)");
        }
    }
}
