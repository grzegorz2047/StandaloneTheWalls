package pl.grzegorz2047.standalonethewalls.mapformat;

/** Finite bounded map-space vector shared by preparation regions and spawns. */
public record MapVector3(double x, double y, double z) {
    public static final double MAXIMUM_ABSOLUTE_COORDINATE = 1_000_000.0d;

    public MapVector3 {
        requireCoordinate(x, "x");
        requireCoordinate(y, "y");
        requireCoordinate(z, "z");
    }

    private static void requireCoordinate(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > MAXIMUM_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    field + " coordinate is outside the supported map range");
        }
    }
}
