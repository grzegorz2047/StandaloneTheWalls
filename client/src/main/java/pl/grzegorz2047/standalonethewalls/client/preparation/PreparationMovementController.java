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
    private static final double VERTICAL_COLLISION_TOLERANCE_METRES = 0.000001d;

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
        PreparationPlayerState postured = applyRequestedPosture(player, world, crouching);
        PreparationPlayerState moved =
                moveHorizontal(
                        postured,
                        world,
                        forwardAxis,
                        rightAxis,
                        sprinting,
                        boundedSeconds);
        PreparationVerticalMotion.Step vertical =
                PreparationVerticalMotion.advance(
                        moved.position().y(),
                        moved.groundHeightMetres(),
                        moved.verticalVelocityMetresPerSecond(),
                        moved.grounded(),
                        jumping && !moved.crouching(),
                        boundedSeconds);
        double limitedHeight =
                world.limitUpwardMovement(
                        moved.position(), vertical.heightMetres(), moved.crouching());
        if (limitedHeight
                < vertical.heightMetres() - VERTICAL_COLLISION_TOLERANCE_METRES) {
            vertical = new PreparationVerticalMotion.Step(limitedHeight, 0.0d, false);
        }
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

    private static PreparationPlayerState applyRequestedPosture(
            PreparationPlayerState player,
            PreparationCollisionWorld world,
            boolean requestedCrouching) {
        if (requestedCrouching) {
            return player.withCrouching(true);
        }
        if (!player.crouching()
                || !world.hasPlayerClearance(player.position(), false)) {
            return player;
        }
        return player.withCrouching(false);
    }

    private static PreparationPlayerState moveHorizontal(
            PreparationPlayerState player,
            PreparationCollisionWorld world,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            double elapsedSeconds) {
        if (elapsedSeconds == 0.0d || (forwardAxis == 0.0d && rightAxis == 0.0d)) {
            return player;
        }
        double magnitude = Math.hypot(forwardAxis, rightAxis);
        double normalizedForward = magnitude > 1.0d ? forwardAxis / magnitude : forwardAxis;
        double normalizedRight = magnitude > 1.0d ? rightAxis / magnitude : rightAxis;
        double speed =
                player.crouching()
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
        PreparationPlayerState moved = tryMove(player, world, deltaX, deltaZ);
        if (moved != player || deltaX == 0.0d || deltaZ == 0.0d) {
            return moved;
        }
        if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
            moved = tryMove(player, world, deltaX, 0.0d);
            return moved != player ? moved : tryMove(player, world, 0.0d, deltaZ);
        }
        moved = tryMove(player, world, 0.0d, deltaZ);
        return moved != player ? moved : tryMove(player, world, deltaX, 0.0d);
    }

    private static PreparationPlayerState tryMove(
            PreparationPlayerState player,
            PreparationCollisionWorld world,
            double deltaX,
            double deltaZ) {
        MapVector3 target = player.horizontalPositionAfter(deltaX, deltaZ);
        if ((Double.compare(target.x(), player.position().x()) == 0
                        && Double.compare(target.z(), player.position().z()) == 0)
                || !world.permitsHorizontal(
                        player.position(), target, false, player.crouching())) {
            return player;
        }

        OptionalDouble highestSupport =
                player.scene().supportMap().highestPlayerCenter(target.x(), target.z());
        if (highestSupport.isEmpty()) {
            return player;
        }
        double supportY = highestSupport.orElseThrow();
        double targetY = player.position().y();
        boolean targetGrounded = player.grounded();
        if (player.grounded()) {
            double supportDelta = supportY - player.position().y();
            if (supportDelta > MAXIMUM_GROUNDED_STEP_METRES + SUPPORT_TOLERANCE_METRES) {
                return player;
            }
            if (supportDelta >= -MAXIMUM_GROUNDED_STEP_METRES - SUPPORT_TOLERANCE_METRES) {
                targetY = supportY;
            } else {
                targetGrounded = false;
            }
        } else if (supportY > player.position().y() + SUPPORT_TOLERANCE_METRES) {
            return player;
        }
        if (!player.scene()
                .obstacleMap()
                .permitsMovement(
                        player.position().x(),
                        player.position().y(),
                        player.position().z(),
                        target.x(),
                        targetY,
                        target.z(),
                        player.crouching())) {
            return player;
        }
        return player.withMovementState(
                target.x(),
                targetY,
                target.z(),
                targetGrounded ? 0.0d : player.verticalVelocityMetresPerSecond(),
                targetGrounded);
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
