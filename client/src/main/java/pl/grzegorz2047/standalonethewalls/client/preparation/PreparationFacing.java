package pl.grzegorz2047.standalonethewalls.client.preparation;

/** Protocol yaw axes shared by preparation movement and camera placement. */
public final class PreparationFacing {
    private PreparationFacing() {
        throw new AssertionError("No instances");
    }

    public static double forwardX(double yawDegrees) {
        return Math.cos(radians(yawDegrees));
    }

    public static double forwardZ(double yawDegrees) {
        return Math.sin(radians(yawDegrees));
    }

    public static double rightX(double yawDegrees) {
        return -Math.sin(radians(yawDegrees));
    }

    public static double rightZ(double yawDegrees) {
        return Math.cos(radians(yawDegrees));
    }

    private static double radians(double yawDegrees) {
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        return Math.toRadians(yawDegrees);
    }
}
