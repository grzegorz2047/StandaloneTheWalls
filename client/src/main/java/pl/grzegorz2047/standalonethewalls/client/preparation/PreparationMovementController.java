package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import java.util.OptionalDouble;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationVerticalMotion;

/** Deterministic preparation movement resolved through region and verified collision guards. */
public final class PreparationMovementController {
    public static final double MOVEMENT_SPEED_METRES_PER_SECOND = 5.0d;
    public static final double SPRINTING_SPEED_METRES_PER_SECOND = 8.0d;
    public static final double CROUCHING_SPEED_METRES_PER_SECOND = 3.0d;
    public static final double MAXIMUM_GROUNDED_STEP_METRES = 0.5d;
    public static final double MAXIMUM_STEP_SECONDS =
            PreparationVerticalMotion.MAXIMUM_STEP_SECONDS;
    public static final double YAW_DEGREES_PER_MOUSE_PIXEL = 0.12d;
    public static final double PITCH_DEGREES_PER_MOUSE_PIXEL = 0.10d;

    private static final double SUPPORT_TOLERANCE_METRES = 0.001d;

    private PreparationMovementController() {
        throw new AssertionError("No instances");
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            double elapsedSeconds) {
        return move(
                current, collisions, forwardAxis, rightAxis, false, false, false, elapsedSeconds);
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            double elapsedSeconds) {
        return move(
                current,
                collisions,
                forwardAxis,
                rightAxis,
                sprinting,
                false,
                false,
                elapsedSeconds);
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            double elapsedSeconds) {
        return move(
                current,
                collisions,
                forwardAxis,
                rightAxis,
                sprinting,
                crouching,
                false,
                elapsedSeconds);
    }

    public static PreparationPlayerState move(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            boolean jumping,
            double elapsedSeconds) {
        PreparationPlayerState player = Objects.requireNonNull(current, "current");
        PreparationCollisionWorld world = Objects.requireNonNull(collisions, "collisions");
        requireAxis(forwardAxis, "forwardAxis");
        requireAxis(rightAxis, "rightAxis");
        requireElapsedSeconds(elapsedSeconds);
        if (sprinting && crouching) {
            throw new IllegalArgumentException("sprinting and crouching are mutually exclusive");
        }
        if (crouching && jumping) {
            throw new IllegalArgumentException("crouching and jumping are mutually exclusive");
        }

        double boundedSeconds = Math.min(elapsedSeconds, MAXIMUM_STEP_SECONDS);
        PreparationPlayerState moved =
                moveHorizontal(
                        player,
                        world,
                        forwardAxis,
                        rightAxis,
                        sprinting,
                        crouching,
                        boundedSeconds);
        PreparationVerticalMotion.Step vertical =
                PreparationVerticalMotion.advance(
                        moved.position().y(),
                        moved.groundHeightMetres(),
                        moved.verticalVelocityMetresPerSecond(),
                        moved.grounded(),
                        jumping,
                        boundedSeconds);
        return moved.withVerticalState(
                vertical.heightMetres(),
                vertical.verticalVelocityMetresPerSecond(),
                vertical.grounded());
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

    private static PreparationPlayerState moveHorizontal(
            PreparationPlayerState player,
            PreparationCollisionWorld world,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            double elapsedSeconds) {
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
        double step = speed * elapsedSeconds;
        double yaw = player.yawDegrees();
        double deltaX =
                step
                        * ((normalizedForward * PreparationFacing.forwardX(yaw))
                                + (normalizedRight * PreparationFacing.rightX(yaw)));
        double deltaZ =
                step
                        * ((normalizedForward * PreparationFacing.forwardZ(yaw))
                                + (normalizedRight * PreparationFacing.rightZ(yaw)));
        MapVector3 target = player.horizontalPositionAfter(deltaX, deltaZ);
        if ((Double.compare(target.x(), player.position().x()) == 0
                        && Double.compare(target.z(), player.position().z()) == 0)
                || !world.permitsHorizontal(player.position(), target, false)) {
            return player;
        }

        OptionalDouble highestSupport =
                player.scene().supportMap().highestPlayerCenter(target.x(), target.z());
        if (highestSupport.isEmpty()) {
            return player;
        }
        double supportY = highestSupport.orElseThrow();
        if (player.grounded()) {
            double supportDelta = supportY - player.position().y();
            if (supportDelta > MAXIMUM_GROUNDED_STEP_METRES + SUPPORT_TOLERANCE_METRES) {
                return player;
            }
            if (supportDelta
                    >= -MAXIMUM_GROUNDED_STEP_METRES - SUPPORT_TOLERANCE_METRES) {
                return player.withMovementState(
                        target.x(), supportY, target.z(), 0.0d, true);
            }
            return player.withMovementState(
                    target.x(), player.position().y(), target.z(), 0.0d, false);
        }
        if (supportY > player.position().y() + SUPPORT_TOLERANCE_METRES) {
            return player;
        }
        return player.withMovementState(
                target.x(),
                player.position().y(),
                target.z(),
                player.verticalVelocityMetresPerSecond(),
                false);
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
