package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;

/** Deterministic preparation movement resolved through region and verified collision guards. */
public final class PreparationMovementController {
    public static final double MOVEMENT_SPEED_METRES_PER_SECOND = 5.0d;
    public static final double SPRINTING_SPEED_METRES_PER_SECOND = 8.0d;
    public static final double CROUCHING_SPEED_METRES_PER_SECOND = 3.0d;
    public static final double MAXIMUM_STEP_SECONDS = 0.1d;
    public static final double YAW_DEGREES_PER_MOUSE_PIXEL = 0.12d;
    public static final double PITCH_DEGREES_PER_MOUSE_PIXEL = 0.10d;

    private PreparationMovementController() {
        throw new AssertionError("No instances");
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            double elapsedSeconds) {
        return move(current, collisions, forwardAxis, rightAxis, false, false, elapsedSeconds);
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            double elapsedSeconds) {
        return move(current, collisions, forwardAxis, rightAxis, sprinting, false, elapsedSeconds);
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            double elapsedSeconds) {
        PreparationPlayerState player = Objects.requireNonNull(current, "current");
        PreparationCollisionWorld world = Objects.requireNonNull(collisions, "collisions");
        requireAxis(forwardAxis, "forwardAxis");
        requireAxis(rightAxis, "rightAxis");
        requireElapsedSeconds(elapsedSeconds);
        if (sprinting && crouching) {
            throw new IllegalArgumentException("sprinting and crouching are mutually exclusive");
        }
        if (elapsedSeconds == 0.0d || (forwardAxis == 0.0d && rightAxis == 0.0d)) {
            return player;
        }

        double magnitude = Math.hypot(forwardAxis, rightAxis);
        double normalizedForward = magnitude > 1.0d ? forwardAxis / magnitude : forwardAxis;
        double normalizedRight = magnitude > 1.0d ? rightAxis / magnitude : rightAxis;
        double speed =
                crouching
                        ? CROUCHING_SPEED_METRES_PER_SECOND
                        : sprinting
                                ? SPRINTING_SPEED_METRES_PER_SECOND
                                : MOVEMENT_SPEED_METRES_PER_SECOND;
        double step = speed * Math.min(elapsedSeconds, MAXIMUM_STEP_SECONDS);
        double yaw = player.yawDegrees();
        double deltaX =
                step
                        * ((normalizedForward * PreparationFacing.forwardX(yaw))
                                + (normalizedRight * PreparationFacing.rightX(yaw)));
        double deltaZ =
                step
                        * ((normalizedForward * PreparationFacing.forwardZ(yaw))
                                + (normalizedRight * PreparationFacing.rightZ(yaw)));
        PreparationPlayerState proposed = player.moveHorizontal(deltaX, deltaZ);
        if (proposed == player
                || !world.permitsHorizontal(player.position(), proposed.position())) {
            return player;
        }
        return proposed;
    }

    public static PreparationPlayerState rotate(
            PreparationPlayerState current, double horizontalMousePixels) {
        return rotate(current, horizontalMousePixels, 0.0d);
    }

    public static PreparationPlayerState rotate(
            PreparationPlayerState current,
            double horizontalMousePixels,
            double verticalMousePixels) {
        PreparationPlayerState player = Objects.requireNonNull(current, "current");
        if (!Double.isFinite(horizontalMousePixels)) {
            throw new IllegalArgumentException("horizontalMousePixels must be finite");
        }
        if (!Double.isFinite(verticalMousePixels)) {
            throw new IllegalArgumentException("verticalMousePixels must be finite");
        }
        return player.rotateView(
                horizontalMousePixels * YAW_DEGREES_PER_MOUSE_PIXEL,
                verticalMousePixels * PITCH_DEGREES_PER_MOUSE_PIXEL);
    }

    private static void requireAxis(double value, String field) {
        if (!Double.isFinite(value) || value < -1.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be finite and in [-1, 1]");
        }
    }

    private static void requireElapsedSeconds(double value) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException("elapsedSeconds must be finite and non-negative");
        }
    }
}
