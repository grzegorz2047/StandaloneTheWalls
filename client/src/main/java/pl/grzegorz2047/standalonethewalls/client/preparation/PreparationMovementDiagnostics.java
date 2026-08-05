package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;

/** Renderer-thread diagnostics derived only from accepted snapshots and locally submitted input. */
public final class PreparationMovementDiagnostics {
    public static final double MAXIMUM_FRAME_SECONDS = 1.0d;
    public static final double DELAYED_SNAPSHOT_AGE_SECONDS = 0.25d;
    public static final double STALE_SNAPSHOT_AGE_SECONDS = 1.0d;
    public static final long DELAYED_ACKNOWLEDGEMENT_LAG_INPUTS = 4L;
    public static final long STALE_ACKNOWLEDGEMENT_LAG_INPUTS = 20L;

    private static final double MAXIMUM_REPORTED_AGE_SECONDS = 99.999d;

    private boolean snapshotReceived;
    private long lastAuthoritativeTick = -1L;
    private long lastAcknowledgedSequence;
    private long highestSubmittedSequence;
    private int pendingPredictionSteps;
    private double snapshotAgeSeconds;

    public void advanceFrame(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds)
                || elapsedSeconds < 0.0d
                || elapsedSeconds > MAXIMUM_FRAME_SECONDS) {
            throw new IllegalArgumentException("elapsedSeconds is outside the diagnostic frame range");
        }
        if (!snapshotReceived || elapsedSeconds == 0.0d) {
            return;
        }
        snapshotAgeSeconds =
                Math.min(MAXIMUM_REPORTED_AGE_SECONDS, snapshotAgeSeconds + elapsedSeconds);
    }

    public void observeLocalState(long submittedSequence, int predictionSteps) {
        if (submittedSequence < highestSubmittedSequence) {
            throw new IllegalArgumentException("submittedSequence regressed");
        }
        if (submittedSequence < lastAcknowledgedSequence) {
            throw new IllegalArgumentException("submittedSequence is behind the acknowledgement");
        }
        validatePredictionSteps(predictionSteps);
        highestSubmittedSequence = submittedSequence;
        pendingPredictionSteps = predictionSteps;
    }

    public void acceptSnapshot(
            long authoritativeTick,
            long acknowledgedSequence,
            long submittedSequence,
            int predictionSteps) {
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick cannot be negative");
        }
        if (snapshotReceived && authoritativeTick <= lastAuthoritativeTick) {
            throw new IllegalArgumentException("authoritativeTick did not advance");
        }
        if (acknowledgedSequence < lastAcknowledgedSequence) {
            throw new IllegalArgumentException("acknowledgedSequence regressed");
        }
        if (submittedSequence < highestSubmittedSequence) {
            throw new IllegalArgumentException("submittedSequence regressed");
        }
        if (acknowledgedSequence < 0L || acknowledgedSequence > submittedSequence) {
            throw new IllegalArgumentException("acknowledgement exceeds submitted input");
        }
        validatePredictionSteps(predictionSteps);

        snapshotReceived = true;
        lastAuthoritativeTick = authoritativeTick;
        lastAcknowledgedSequence = acknowledgedSequence;
        highestSubmittedSequence = submittedSequence;
        pendingPredictionSteps = predictionSteps;
        snapshotAgeSeconds = 0.0d;
    }

    public Snapshot current() {
        if (!snapshotReceived) {
            return new Snapshot(false, -1L, 0L, pendingPredictionSteps, Quality.WAITING);
        }
        long ageMillis = Math.round(snapshotAgeSeconds * 1_000.0d);
        long acknowledgementLag = highestSubmittedSequence - lastAcknowledgedSequence;
        return new Snapshot(
                true,
                ageMillis,
                acknowledgementLag,
                pendingPredictionSteps,
                classify(snapshotAgeSeconds, acknowledgementLag));
    }

    private static Quality classify(double ageSeconds, long acknowledgementLag) {
        if (ageSeconds >= STALE_SNAPSHOT_AGE_SECONDS
                || acknowledgementLag >= STALE_ACKNOWLEDGEMENT_LAG_INPUTS) {
            return Quality.STALE;
        }
        if (ageSeconds >= DELAYED_SNAPSHOT_AGE_SECONDS
                || acknowledgementLag >= DELAYED_ACKNOWLEDGEMENT_LAG_INPUTS) {
            return Quality.DELAYED;
        }
        return Quality.GOOD;
    }

    private static void validatePredictionSteps(int predictionSteps) {
        if (predictionSteps < 0
                || predictionSteps > PreparationPredictionHistory.DEFAULT_MAXIMUM_STEPS) {
            throw new IllegalArgumentException("predictionSteps is outside the bounded history");
        }
    }

    public enum Quality {
        WAITING,
        GOOD,
        DELAYED,
        STALE
    }

    public record Snapshot(
            boolean snapshotAvailable,
            long snapshotAgeMillis,
            long acknowledgementLagInputs,
            int pendingPredictionSteps,
            Quality quality) {
        public Snapshot {
            Objects.requireNonNull(quality, "quality");
            if (snapshotAvailable != (snapshotAgeMillis >= 0L)) {
                throw new IllegalArgumentException("snapshot availability does not match its age");
            }
            if (acknowledgementLagInputs < 0L) {
                throw new IllegalArgumentException("acknowledgementLagInputs cannot be negative");
            }
            validatePredictionSteps(pendingPredictionSteps);
            if (!snapshotAvailable && quality != Quality.WAITING) {
                throw new IllegalArgumentException("missing snapshot must have WAITING quality");
            }
            if (snapshotAvailable && quality == Quality.WAITING) {
                throw new IllegalArgumentException("available snapshot cannot have WAITING quality");
            }
        }
    }
}
