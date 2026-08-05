package pl.grzegorz2047.standalonethewalls.protocol.preparation;

/** One bounded client movement intent for an authoritative preparation round. */
public record PreparationInput(
        long roundNumber,
        long sequence,
        int forwardAxis,
        int rightAxis,
        boolean sprinting,
        int yawCentidegrees,
        int pitchCentidegrees) {
    public static final int MAXIMUM_AXIS = 127;
    public static final int MINIMUM_YAW_CENTIDEGREES = -18_000;
    public static final int MAXIMUM_YAW_CENTIDEGREES = 17_999;
    public static final int MINIMUM_PITCH_CENTIDEGREES = -8_500;
    public static final int MAXIMUM_PITCH_CENTIDEGREES = 8_500;

    public PreparationInput {
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        requireAxis(forwardAxis, "forwardAxis");
        requireAxis(rightAxis, "rightAxis");
        if (yawCentidegrees < MINIMUM_YAW_CENTIDEGREES
                || yawCentidegrees > MAXIMUM_YAW_CENTIDEGREES) {
            throw new IllegalArgumentException("yawCentidegrees is outside [-18000, 17999]");
        }
        if (pitchCentidegrees < MINIMUM_PITCH_CENTIDEGREES
                || pitchCentidegrees > MAXIMUM_PITCH_CENTIDEGREES) {
            throw new IllegalArgumentException("pitchCentidegrees is outside [-8500, 8500]");
        }
    }

    public double forwardAxisValue() {
        return (double) forwardAxis / MAXIMUM_AXIS;
    }

    public double rightAxisValue() {
        return (double) rightAxis / MAXIMUM_AXIS;
    }

    public double yawDegrees() {
        return yawCentidegrees / 100.0d;
    }

    public double pitchDegrees() {
        return pitchCentidegrees / 100.0d;
    }

    private static void requireAxis(int value, String field) {
        if (value < -MAXIMUM_AXIS || value > MAXIMUM_AXIS) {
            throw new IllegalArgumentException(field + " is outside [-127, 127]");
        }
    }
}
