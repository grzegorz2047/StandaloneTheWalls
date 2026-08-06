package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Bounded renderer-thread history used to replay only local prediction that the server has not yet
 * acknowledged.
 */
public final class PreparationPredictionHistory {
    public static final int DEFAULT_MAXIMUM_STEPS = 256;

    private final int maximumSteps;
    private final ArrayDeque<PredictionStep> pending = new ArrayDeque<>();

    private long lastAcknowledgedSequence;
    private long highestSubmittedSequence;

    public PreparationPredictionHistory() {
        this(DEFAULT_MAXIMUM_STEPS);
    }

    PreparationPredictionHistory(int maximumSteps) {
        if (maximumSteps < 1) {
            throw new IllegalArgumentException("maximumSteps must be positive");
        }
        this.maximumSteps = maximumSteps;
    }

    public PreparationPlayerState predict(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            long sequence,
            double forwardAxis,
            double rightAxis,
            double elapsedSeconds) {
        return predict(
                current,
                collisions,
                sequence,
                forwardAxis,
                rightAxis,
                false,
                false,
                false,
                elapsedSeconds);
    }

    public PreparationPlayerState predict(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            long sequence,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            double elapsedSeconds) {
        return predict(
                current,
                collisions,
                sequence,
                forwardAxis,
                rightAxis,
                sprinting,
                false,
                false,
                elapsedSeconds);
    }

    public PreparationPlayerState predict(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            long sequence,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            double elapsedSeconds) {
        return predict(
                current,
                collisions,
                sequence,
                forwardAxis,
                rightAxis,
                sprinting,
                crouching,
                false,
                elapsedSeconds);
    }

    public PreparationPlayerState predict(
            PreparationPlayerState current,
            PreparationCollisionWorld collisions,
            long sequence,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            boolean jumping,
            double elapsedSeconds) {
        PreparationPlayerState player = Objects.requireNonNull(current, "current");
        PreparationCollisionWorld world = Objects.requireNonNull(collisions, "collisions");
        requireCurrentSequence(sequence);
        if (pending.size() == maximumSteps) {
            throw new IllegalStateException("preparation prediction history is full");
        }
        PredictionStep step =
                new PredictionStep(
                        sequence,
                        forwardAxis,
                        rightAxis,
                        sprinting,
                        crouching,
                        jumping,
                        player.yawDegrees(),
                        player.pitchDegrees(),
                        elapsedSeconds);
        pending.addLast(step);
        return apply(player, world, step);
    }

    public void markSubmitted(long sequence) {
        if (sequence != highestSubmittedSequence + 1L) {
            throw new IllegalArgumentException(
                    "submitted preparation input sequence must advance exactly once");
        }
        if (sequence <= lastAcknowledgedSequence) {
            throw new IllegalArgumentException(
                    "submitted preparation input sequence was already acknowledged");
        }
        highestSubmittedSequence = sequence;
    }

    public PreparationPlayerState reconcile(
            PreparationPlayerState authoritative,
            PreparationCollisionWorld collisions,
            long acknowledgedSequence) {
        PreparationPlayerState state = Objects.requireNonNull(authoritative, "authoritative");
        PreparationCollisionWorld world = Objects.requireNonNull(collisions, "collisions");
        if (acknowledgedSequence < lastAcknowledgedSequence) {
            throw new IllegalArgumentException("preparation acknowledgement regressed");
        }
        if (acknowledgedSequence > highestSubmittedSequence) {
            throw new IllegalArgumentException(
                    "preparation acknowledgement exceeds submitted input");
        }
        while (!pending.isEmpty() && pending.getFirst().sequence() <= acknowledgedSequence) {
            pending.removeFirst();
        }
        lastAcknowledgedSequence = acknowledgedSequence;
        for (PredictionStep step : pending) {
            state = apply(state, world, step);
        }
        return state;
    }

    public int pendingStepCount() {
        return pending.size();
    }

    public long lastAcknowledgedSequence() {
        return lastAcknowledgedSequence;
    }

    public long highestSubmittedSequence() {
        return highestSubmittedSequence;
    }

    private void requireCurrentSequence(long sequence) {
        if (sequence != highestSubmittedSequence + 1L) {
            throw new IllegalArgumentException(
                    "predicted preparation sequence must be the next unsubmitted sequence");
        }
        if (sequence <= lastAcknowledgedSequence) {
            throw new IllegalArgumentException("predicted preparation sequence was acknowledged");
        }
    }

    private static PreparationPlayerState apply(
            PreparationPlayerState state,
            PreparationCollisionWorld collisions,
            PredictionStep step) {
        PreparationPlayerState oriented =
                state.withAuthoritativeState(
                        state.position().x(),
                        state.position().y(),
                        state.position().z(),
                        state.verticalVelocityMetresPerSecond(),
                        state.grounded(),
                        step.yawDegrees(),
                        step.pitchDegrees());
        return PreparationMovementController.move(
                oriented,
                collisions,
                step.forwardAxis(),
                step.rightAxis(),
                step.sprinting(),
                step.crouching(),
                step.jumping(),
                step.elapsedSeconds());
    }

    private record PredictionStep(
            long sequence,
            double forwardAxis,
            double rightAxis,
            boolean sprinting,
            boolean crouching,
            boolean jumping,
            double yawDegrees,
            double pitchDegrees,
            double elapsedSeconds) {
        private PredictionStep {
            if (sequence < 1L) {
                throw new IllegalArgumentException("sequence must be positive");
            }
            requireAxis(forwardAxis, "forwardAxis");
            requireAxis(rightAxis, "rightAxis");
            if (sprinting && crouching) {
                throw new IllegalArgumentException(
                        "sprinting and crouching are mutually exclusive");
            }
            if (crouching && jumping) {
                throw new IllegalArgumentException(
                        "crouching and jumping are mutually exclusive");
            }
            if (!Double.isFinite(yawDegrees) || yawDegrees < -180.0d || yawDegrees >= 180.0d) {
                throw new IllegalArgumentException("yawDegrees must be in [-180, 180)");
            }
            if (!Double.isFinite(pitchDegrees)
                    || pitchDegrees < PreparationPlayerState.MINIMUM_PITCH_DEGREES
                    || pitchDegrees > PreparationPlayerState.MAXIMUM_PITCH_DEGREES) {
                throw new IllegalArgumentException("pitchDegrees is outside the supported range");
            }
            if (!Double.isFinite(elapsedSeconds)
                    || elapsedSeconds < 0.0d
                    || elapsedSeconds > PreparationMovementController.MAXIMUM_STEP_SECONDS) {
                throw new IllegalArgumentException(
                        "elapsedSeconds must be in [0, maximum movement step]");
            }
        }

        private static void requireAxis(double value, String field) {
            if (!Double.isFinite(value) || value < -1.0d || value > 1.0d) {
                throw new IllegalArgumentException(field + " must be finite and in [-1, 1]");
            }
        }
    }
}
