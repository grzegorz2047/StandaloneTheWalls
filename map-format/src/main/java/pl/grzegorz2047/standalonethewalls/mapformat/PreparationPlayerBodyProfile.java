package pl.grzegorz2047.standalonethewalls.mapformat;

/** Shared authoritative dimensions for the preparation player collision body. */
public final class PreparationPlayerBodyProfile {
    public static final double HORIZONTAL_RADIUS_METRES = 0.35d;
    public static final double FEET_OFFSET_BELOW_POSITION_METRES = 0.5d;
    public static final double STANDING_HEIGHT_METRES = 1.8d;
    public static final double CROUCHING_HEIGHT_METRES = 1.1d;

    private PreparationPlayerBodyProfile() {
        throw new AssertionError("No instances");
    }

    public static double minimumY(double positionYMetres) {
        requireFinite(positionYMetres, "positionYMetres");
        return positionYMetres - FEET_OFFSET_BELOW_POSITION_METRES;
    }

    public static double maximumY(double positionYMetres, boolean crouching) {
        return minimumY(positionYMetres) + height(crouching);
    }

    public static double headOffsetFromPosition(boolean crouching) {
        return height(crouching) - FEET_OFFSET_BELOW_POSITION_METRES;
    }

    public static double height(boolean crouching) {
        return crouching ? CROUCHING_HEIGHT_METRES : STANDING_HEIGHT_METRES;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
