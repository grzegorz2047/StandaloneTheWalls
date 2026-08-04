package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;

/** One bounded, exclusive spawn candidate owned by an authoritative team. */
public record PreparationSpawnPoint(
        int index, TeamId team, double x, double y, double z, double yawDegrees) {
    public static final int MAXIMUM_INDEX = 4_095;
    public static final double MAXIMUM_ABSOLUTE_COORDINATE = 1_000_000.0d;

    public PreparationSpawnPoint {
        if (index < 0 || index > MAXIMUM_INDEX) {
            throw new IllegalArgumentException("spawn index is outside the supported range");
        }
        Objects.requireNonNull(team, "team");
        requireCoordinate(x, "x");
        requireCoordinate(y, "y");
        requireCoordinate(z, "z");
        if (!Double.isFinite(yawDegrees) || yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw new IllegalArgumentException("spawn yaw must be finite and in [-180, 180)");
        }
    }

    private static void requireCoordinate(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > MAXIMUM_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(field + " coordinate is outside the supported range");
        }
    }
}
