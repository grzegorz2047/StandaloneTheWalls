package pl.grzegorz2047.standalonethewalls.protocol.preparation;

/** Shared deterministic flat-ground vertical motion used by client prediction and server ticks. */
public final class PreparationVerticalMotion {
    public static final double JUMP_IMPULSE_METRES_PER_SECOND = 6.0d;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 18.0d;
    public static final double MAXIMUM_FALL_SPEED_METRES_PER_SECOND = 30.0d;
    public static final double MAXIMUM_STEP_SECONDS = 0.1d;
    public static final int MINIMUM_VERTICAL_VELOCITY_MILLIMETRES_PER_SECOND = -30_000;
    public static final int MAXIMUM_VERTICAL_VELOCITY_MILLIMETRES_PER_SECOND = 6_000;

    private static final double GROUND_EPSILON_METRES = 0.000001d;

    private PreparationVerticalMotion() {
        throw new AssertionError("No instances");
    }

    public static Step advance(
            double heightMetres,
            double groundHeightMetres,
            double verticalVelocityMetresPerSecond,
            boolean grounded,
            boolean jumpRequested,
            double elapsedSeconds) {
        requireFinite(heightMetres, "heightMetres");
        requireFinite(groundHeightMetres, "groundHeightMetres");
        requireFinite(verticalVelocityMetresPerSecond, "verticalVelocityMetresPerSecond");
        if (heightMetres < groundHeightMetres - GROUND_EPSILON_METRES) {
            throw new IllegalArgumentException("heightMetres cannot be below groundHeightMetres");
        }
        if (verticalVelocityMetresPerSecond < -MAXIMUM_FALL_SPEED_METRES_PER_SECOND
                || verticalVelocityMetresPerSecond > JUMP_IMPULSE_METRES_PER_SECOND) {
            throw new IllegalArgumentException("vertical velocity is outside the supported range");
        }
        if (grounded && Double.compare(verticalVelocityMetresPerSecond, 0.0d) != 0) {
            throw new IllegalArgumentException("grounded motion must have zero vertical velocity");
        }
        if (!Double.isFinite(elapsedSeconds)
                || elapsedSeconds < 0.0d
                || elapsedSeconds > MAXIMUM_STEP_SECONDS) {
            throw new IllegalArgumentException("elapsedSeconds is outside [0, 0.1]");
        }

        double height = grounded ? groundHeightMetres : heightMetres;
        double velocity = grounded ? 0.0d : verticalVelocityMetresPerSecond;
        boolean onGround = grounded;
        if (onGround && jumpRequested) {
            velocity = JUMP_IMPULSE_METRES_PER_SECOND;
            onGround = false;
        }
        if (onGround || elapsedSeconds == 0.0d) {
            return new Step(height, velocity, onGround);
        }

        double remainingSeconds = elapsedSeconds;
        double nextHeight = height;
        if (velocity > -MAXIMUM_FALL_SPEED_METRES_PER_SECOND) {
            double secondsUntilTerminal =
                    (velocity + MAXIMUM_FALL_SPEED_METRES_PER_SECOND)
                            / GRAVITY_METRES_PER_SECOND_SQUARED;
            double acceleratedSeconds = Math.min(remainingSeconds, secondsUntilTerminal);
            nextHeight +=
                    (velocity * acceleratedSeconds)
                            - (0.5d
                                    * GRAVITY_METRES_PER_SECOND_SQUARED
                                    * acceleratedSeconds
                                    * acceleratedSeconds);
            velocity -= GRAVITY_METRES_PER_SECOND_SQUARED * acceleratedSeconds;
            remainingSeconds -= acceleratedSeconds;
        }
        if (remainingSeconds > 0.0d) {
            velocity = -MAXIMUM_FALL_SPEED_METRES_PER_SECOND;
            nextHeight += velocity * remainingSeconds;
        }
        if (nextHeight <= groundHeightMetres + GROUND_EPSILON_METRES) {
            return new Step(groundHeightMetres, 0.0d, true);
        }
        return new Step(
                nextHeight, Math.max(velocity, -MAXIMUM_FALL_SPEED_METRES_PER_SECOND), false);
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    public record Step(
            double heightMetres, double verticalVelocityMetresPerSecond, boolean grounded) {
        public Step {
            requireFinite(heightMetres, "heightMetres");
            requireFinite(verticalVelocityMetresPerSecond, "verticalVelocityMetresPerSecond");
            if (verticalVelocityMetresPerSecond < -MAXIMUM_FALL_SPEED_METRES_PER_SECOND
                    || verticalVelocityMetresPerSecond > JUMP_IMPULSE_METRES_PER_SECOND) {
                throw new IllegalArgumentException(
                        "verticalVelocityMetresPerSecond is outside the supported range");
            }
            if (grounded && Double.compare(verticalVelocityMetresPerSecond, 0.0d) != 0) {
                throw new IllegalArgumentException(
                        "grounded step must have zero vertical velocity");
            }
        }
    }
}
