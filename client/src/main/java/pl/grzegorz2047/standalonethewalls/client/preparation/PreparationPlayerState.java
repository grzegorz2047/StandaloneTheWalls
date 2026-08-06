package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import java.util.OptionalDouble;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationVerticalMotion;

/** Immutable local preparation player and view state constrained to verified active bounds. */
public final class PreparationPlayerState {
    public static final double MINIMUM_PITCH_DEGREES = -85.0d;
    public static final double MAXIMUM_PITCH_DEGREES = 85.0d;

    private static final double SUPPORT_TOLERANCE_METRES = 0.001d;

    private final VerifiedPreparationScene scene;
    private final MapVector3 position;
    private final double verticalVelocityMetresPerSecond;
    private final boolean grounded;
    private final boolean crouching;
    private final PreparationBarrierPolicy storedBarrierPolicy;
    private final double yawDegrees;
    private final double pitchDegrees;

    private PreparationPlayerState(
            VerifiedPreparationScene scene,
            MapVector3 position,
            double verticalVelocityMetresPerSecond,
            boolean grounded,
            boolean crouching,
            PreparationBarrierPolicy barrierPolicy,
            double yawDegrees,
            double pitchDegrees) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.position = Objects.requireNonNull(position, "position");
        PreparationBarrierPolicy requested =
                Objects.requireNonNull(barrierPolicy, "barrierPolicy");
        storedBarrierPolicy =
                scene.barrierPolicy() == PreparationBarrierPolicy.OPEN
                        ? PreparationBarrierPolicy.OPEN
                        : requested;
        boolean insideBounds =
                storedBarrierPolicy == PreparationBarrierPolicy.OPEN
                        ? scene.worldBounds().contains(position)
                        : scene.region().contains(position);
        if (!insideBounds) {
            throw new IllegalArgumentException(
                    "preparation player position must remain inside the active verified bounds");
        }
        requireFinite(verticalVelocityMetresPerSecond, "verticalVelocityMetresPerSecond");
        if (verticalVelocityMetresPerSecond
                        < -PreparationVerticalMotion.MAXIMUM_FALL_SPEED_METRES_PER_SECOND
                || verticalVelocityMetresPerSecond
                        > PreparationVerticalMotion.JUMP_IMPULSE_METRES_PER_SECOND) {
            throw new IllegalArgumentException("vertical velocity is outside the supported range");
        }
        if (grounded && Double.compare(verticalVelocityMetresPerSecond, 0.0d) != 0) {
            throw new IllegalArgumentException("grounded player must have zero vertical velocity");
        }
        double support = supportAtOrBelow(scene, position);
        if (position.y() < support - SUPPORT_TOLERANCE_METRES) {
            throw new IllegalArgumentException("preparation player cannot be below map support");
        }
        if (grounded && Math.abs(position.y() - support) > SUPPORT_TOLERANCE_METRES) {
            throw new IllegalArgumentException("grounded player must remain on map support");
        }
        if (!scene.obstacleMap()
                .hasPlayerClearance(
                        position.x(),
                        position.y(),
                        position.z(),
                        crouching,
                        storedBarrierPolicy)) {
            throw new IllegalArgumentException(
                    "preparation player body overlaps a verified obstacle");
        }
        this.verticalVelocityMetresPerSecond = verticalVelocityMetresPerSecond;
        this.grounded = grounded;
        this.crouching = crouching;
        this.yawDegrees = requireNormalizedYaw(yawDegrees);
        this.pitchDegrees = requirePitch(pitchDegrees);
    }

    public static PreparationPlayerState atAuthoritativeSpawn(VerifiedPreparationScene scene) {
        VerifiedPreparationScene verifiedScene = Objects.requireNonNull(scene, "scene");
        return new PreparationPlayerState(
                verifiedScene,
                verifiedScene.spawn().position(),
                0.0d,
                true,
                false,
                PreparationBarrierPolicy.CLOSED,
                normalizeYaw(verifiedScene.spawn().yawDegrees()),
                0.0d);
    }

    public VerifiedPreparationScene scene() {
        return scene;
    }

    public MapVector3 position() {
        return position;
    }

    public double groundHeightMetres() {
        return supportAtOrBelow(scene, position);
    }

    public double verticalVelocityMetresPerSecond() {
        return verticalVelocityMetresPerSecond;
    }

    public boolean grounded() {
        return grounded;
    }

    public boolean crouching() {
        return crouching;
    }

    public PreparationBarrierPolicy barrierPolicy() {
        return scene.barrierPolicy() == PreparationBarrierPolicy.OPEN
                ? PreparationBarrierPolicy.OPEN
                : storedBarrierPolicy;
    }

    public double yawDegrees() {
        return yawDegrees;
    }

    public double pitchDegrees() {
        return pitchDegrees;
    }

    public PreparationPlayerState withBarrierPolicy(PreparationBarrierPolicy nextPolicy) {
        PreparationBarrierPolicy requested = Objects.requireNonNull(nextPolicy, "nextPolicy");
        PreparationBarrierPolicy current = barrierPolicy();
        if (requested == current) {
            return this;
        }
        if (current == PreparationBarrierPolicy.OPEN) {
            throw new IllegalArgumentException(
                    "central barriers cannot close again during the local round");
        }
        return new PreparationPlayerState(
                scene,
                position,
                verticalVelocityMetresPerSecond,
                grounded,
                crouching,
                requested,
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState withAuthoritativeState(
            double x,
            double y,
            double z,
            double authoritativeYawDegrees,
            double authoritativePitchDegrees) {
        return withAuthoritativeState(
                x, y, z, 0.0d, true, crouching, authoritativeYawDegrees, authoritativePitchDegrees);
    }

    public PreparationPlayerState withAuthoritativeState(
            double x,
            double y,
            double z,
            double authoritativeVerticalVelocityMetresPerSecond,
            boolean authoritativeGrounded,
            double authoritativeYawDegrees,
            double authoritativePitchDegrees) {
        return withAuthoritativeState(
                x,
                y,
                z,
                authoritativeVerticalVelocityMetresPerSecond,
                authoritativeGrounded,
                crouching,
                authoritativeYawDegrees,
                authoritativePitchDegrees);
    }

    public PreparationPlayerState withAuthoritativeState(
            double x,
            double y,
            double z,
            double authoritativeVerticalVelocityMetresPerSecond,
            boolean authoritativeGrounded,
            boolean authoritativeCrouching,
            double authoritativeYawDegrees,
            double authoritativePitchDegrees) {
        return new PreparationPlayerState(
                scene,
                new MapVector3(x, y, z),
                authoritativeVerticalVelocityMetresPerSecond,
                authoritativeGrounded,
                authoritativeCrouching,
                barrierPolicy(),
                normalizeYaw(authoritativeYawDegrees),
                authoritativePitchDegrees);
    }

    public PreparationPlayerState withMovementState(
            double x,
            double y,
            double z,
            double nextVerticalVelocityMetresPerSecond,
            boolean nextGrounded) {
        return withMovementState(
                x, y, z, nextVerticalVelocityMetresPerSecond, nextGrounded, crouching);
    }

    public PreparationPlayerState withMovementState(
            double x,
            double y,
            double z,
            double nextVerticalVelocityMetresPerSecond,
            boolean nextGrounded,
            boolean nextCrouching) {
        return new PreparationPlayerState(
                scene,
                new MapVector3(x, y, z),
                nextVerticalVelocityMetresPerSecond,
                nextGrounded,
                nextCrouching,
                barrierPolicy(),
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState withCrouching(boolean nextCrouching) {
        if (nextCrouching == crouching) {
            return this;
        }
        return new PreparationPlayerState(
                scene,
                position,
                verticalVelocityMetresPerSecond,
                grounded,
                nextCrouching,
                barrierPolicy(),
                yawDegrees,
                pitchDegrees);
    }

    public MapVector3 horizontalPositionAfter(double deltaX, double deltaZ) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaZ, "deltaZ");
        double requestedX = addFinite(position.x(), deltaX);
        double requestedZ = addFinite(position.z(), deltaZ);
        if (barrierPolicy() == PreparationBarrierPolicy.OPEN) {
            PreparationWorldBounds bounds = scene.worldBounds();
            return new MapVector3(
                    bounds.clampX(requestedX), position.y(), bounds.clampZ(requestedZ));
        }
        PreparationRegion region = scene.region();
        return new MapVector3(
                clamp(requestedX, region.minimum().x(), region.maximum().x()),
                position.y(),
                clamp(requestedZ, region.minimum().z(), region.maximum().z()));
    }

    public PreparationPlayerState moveHorizontal(double deltaX, double deltaZ) {
        MapVector3 target = horizontalPositionAfter(deltaX, deltaZ);
        if (Double.compare(target.x(), position.x()) == 0
                && Double.compare(target.z(), position.z()) == 0) {
            return this;
        }
        return new PreparationPlayerState(
                scene,
                target,
                verticalVelocityMetresPerSecond,
                grounded,
                crouching,
                barrierPolicy(),
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState withVerticalState(
            double heightMetres, double nextVerticalVelocityMetresPerSecond, boolean nextGrounded) {
        requireFinite(heightMetres, "heightMetres");
        if (Double.compare(heightMetres, position.y()) == 0
                && Double.compare(
                                nextVerticalVelocityMetresPerSecond,
                                verticalVelocityMetresPerSecond)
                        == 0
                && nextGrounded == grounded) {
            return this;
        }
        return new PreparationPlayerState(
                scene,
                new MapVector3(position.x(), heightMetres, position.z()),
                nextVerticalVelocityMetresPerSecond,
                nextGrounded,
                crouching,
                barrierPolicy(),
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState rotate(double deltaDegrees) {
        return rotateView(deltaDegrees, 0.0d);
    }

    public PreparationPlayerState rotateView(double deltaYawDegrees, double deltaPitchDegrees) {
        requireFinite(deltaYawDegrees, "deltaYawDegrees");
        requireFinite(deltaPitchDegrees, "deltaPitchDegrees");
        double nextYaw = normalizeYaw(addFinite(yawDegrees, deltaYawDegrees));
        double nextPitch =
                clamp(
                        addFinite(pitchDegrees, deltaPitchDegrees),
                        MINIMUM_PITCH_DEGREES,
                        MAXIMUM_PITCH_DEGREES);
        if (Double.compare(nextYaw, yawDegrees) == 0
                && Double.compare(nextPitch, pitchDegrees) == 0) {
            return this;
        }
        return new PreparationPlayerState(
                scene,
                position,
                verticalVelocityMetresPerSecond,
                grounded,
                crouching,
                barrierPolicy(),
                nextYaw,
                nextPitch);
    }

    private static double supportAtOrBelow(VerifiedPreparationScene scene, MapVector3 position) {
        OptionalDouble support =
                scene.supportMap()
                        .highestPlayerCenterAtOrBelow(
                                position.x(),
                                position.z(),
                                position.y() + SUPPORT_TOLERANCE_METRES);
        if (support.isEmpty()) {
            throw new IllegalArgumentException(
                    "preparation player position has no verified support below it");
        }
        return support.orElseThrow();
    }

    private static double addFinite(double value, double delta) {
        double result = value + delta;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("preparation state update overflowed finite range");
        }
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double normalizeYaw(double value) {
        requireFinite(value, "yawDegrees");
        double normalized = value % 360.0d;
        if (normalized >= 180.0d) {
            normalized -= 360.0d;
        } else if (normalized < -180.0d) {
            normalized += 360.0d;
        }
        return normalized == -0.0d ? 0.0d : normalized;
    }

    private static double requireNormalizedYaw(double value) {
        if (!Double.isFinite(value) || value < -180.0d || value >= 180.0d) {
            throw new IllegalArgumentException(
                    "preparation player yaw must be finite and in [-180, 180)");
        }
        return value;
    }

    private static double requirePitch(double value) {
        if (!Double.isFinite(value)
                || value < MINIMUM_PITCH_DEGREES
                || value > MAXIMUM_PITCH_DEGREES) {
            throw new IllegalArgumentException(
                    "preparation player pitch must be finite and in [-85, 85]");
        }
        return value;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
