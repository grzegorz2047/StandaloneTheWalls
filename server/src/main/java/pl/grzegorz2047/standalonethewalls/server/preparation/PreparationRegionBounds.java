package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;

/** Fixed-point authoritative horizontal boundary for one preparation team. */
public record PreparationRegionBounds(
        TeamId team,
        int minimumXMillimetres,
        int minimumYMillimetres,
        int minimumZMillimetres,
        int maximumXMillimetres,
        int maximumYMillimetres,
        int maximumZMillimetres) {
    public PreparationRegionBounds {
        Objects.requireNonNull(team, "team");
        if (minimumXMillimetres >= maximumXMillimetres
                || minimumYMillimetres >= maximumYMillimetres
                || minimumZMillimetres >= maximumZMillimetres) {
            throw new IllegalArgumentException(
                    "preparation region minimum must be smaller than maximum on every axis");
        }
    }

    public static PreparationRegionBounds from(TeamId team, PreparationRegion region) {
        PreparationRegion verified = Objects.requireNonNull(region, "region");
        return new PreparationRegionBounds(
                Objects.requireNonNull(team, "team"),
                toMillimetres(verified.minimum().x()),
                toMillimetres(verified.minimum().y()),
                toMillimetres(verified.minimum().z()),
                toMillimetres(verified.maximum().x()),
                toMillimetres(verified.maximum().y()),
                toMillimetres(verified.maximum().z()));
    }

    public double clampX(double valueMillimetres) {
        return clamp(valueMillimetres, minimumXMillimetres, maximumXMillimetres);
    }

    public double clampZ(double valueMillimetres) {
        return clamp(valueMillimetres, minimumZMillimetres, maximumZMillimetres);
    }

    public boolean contains(int xMillimetres, int yMillimetres, int zMillimetres) {
        return xMillimetres >= minimumXMillimetres
                && xMillimetres <= maximumXMillimetres
                && yMillimetres >= minimumYMillimetres
                && yMillimetres <= maximumYMillimetres
                && zMillimetres >= minimumZMillimetres
                && zMillimetres <= maximumZMillimetres;
    }

    private static int toMillimetres(double metres) {
        if (!Double.isFinite(metres)) {
            throw new IllegalArgumentException("preparation region coordinate must be finite");
        }
        long millimetres = Math.round(metres * 1_000.0d);
        if (millimetres < Integer.MIN_VALUE || millimetres > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "preparation region coordinate exceeds fixed-point range");
        }
        return (int) millimetres;
    }

    private static double clamp(double value, int minimum, int maximum) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("preparation movement coordinate must be finite");
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
