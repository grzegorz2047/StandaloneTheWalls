package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** One authoritative fixed-point player state inside a preparation world snapshot. */
public record PreparationPlayerSnapshot(
        PlayerId playerId,
        long lastProcessedInputSequence,
        int xMillimetres,
        int yMillimetres,
        int zMillimetres,
        int verticalVelocityMillimetresPerSecond,
        boolean grounded,
        boolean crouching,
        int yawCentidegrees,
        int pitchCentidegrees) {
    public static final int MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES = 1_000_000_000;

    public PreparationPlayerSnapshot(
            PlayerId playerId,
            long lastProcessedInputSequence,
            int xMillimetres,
            int yMillimetres,
            int zMillimetres,
            int yawCentidegrees,
            int pitchCentidegrees) {
        this(
                playerId,
                lastProcessedInputSequence,
                xMillimetres,
                yMillimetres,
                zMillimetres,
                0,
                true,
                false,
                yawCentidegrees,
                pitchCentidegrees);
    }

    public PreparationPlayerSnapshot(
            PlayerId playerId,
            long lastProcessedInputSequence,
            int xMillimetres,
            int yMillimetres,
            int zMillimetres,
            boolean crouching,
            int yawCentidegrees,
            int pitchCentidegrees) {
        this(
                playerId,
                lastProcessedInputSequence,
                xMillimetres,
                yMillimetres,
                zMillimetres,
                0,
                true,
                crouching,
                yawCentidegrees,
                pitchCentidegrees);
    }

    public PreparationPlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (lastProcessedInputSequence < 0L) {
            throw new IllegalArgumentException("lastProcessedInputSequence cannot be negative");
        }
        requireCoordinate(xMillimetres, "xMillimetres");
        requireCoordinate(yMillimetres, "yMillimetres");
        requireCoordinate(zMillimetres, "zMillimetres");
        if (verticalVelocityMillimetresPerSecond
                        < PreparationVerticalMotion.MINIMUM_VERTICAL_VELOCITY_MILLIMETRES_PER_SECOND
                || verticalVelocityMillimetresPerSecond
                        > PreparationVerticalMotion
                                .MAXIMUM_VERTICAL_VELOCITY_MILLIMETRES_PER_SECOND) {
            throw new IllegalArgumentException(
                    "verticalVelocityMillimetresPerSecond is outside the supported range");
        }
        if (grounded && verticalVelocityMillimetresPerSecond != 0) {
            throw new IllegalArgumentException(
                    "grounded snapshot must have zero vertical velocity");
        }
        if (yawCentidegrees < PreparationInput.MINIMUM_YAW_CENTIDEGREES
                || yawCentidegrees > PreparationInput.MAXIMUM_YAW_CENTIDEGREES) {
            throw new IllegalArgumentException("yawCentidegrees is outside [-18000, 17999]");
        }
        if (pitchCentidegrees < PreparationInput.MINIMUM_PITCH_CENTIDEGREES
                || pitchCentidegrees > PreparationInput.MAXIMUM_PITCH_CENTIDEGREES) {
            throw new IllegalArgumentException("pitchCentidegrees is outside [-8500, 8500]");
        }
    }

    public double xMetres() {
        return xMillimetres / 1_000.0d;
    }

    public double yMetres() {
        return yMillimetres / 1_000.0d;
    }

    public double zMetres() {
        return zMillimetres / 1_000.0d;
    }

    public double verticalVelocityMetresPerSecond() {
        return verticalVelocityMillimetresPerSecond / 1_000.0d;
    }

    public double yawDegrees() {
        return yawCentidegrees / 100.0d;
    }

    public double pitchDegrees() {
        return pitchCentidegrees / 100.0d;
    }

    private static void requireCoordinate(int value, String field) {
        if (value < -MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES
                || value > MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES) {
            throw new IllegalArgumentException(field + " is outside the supported world range");
        }
    }
}
