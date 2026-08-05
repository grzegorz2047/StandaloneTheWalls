package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;

/** Constant-memory latest-wins handoff from one receive watcher to the coordinator tick. */
public final class PreparationInputMailbox {
    private long roundNumber;
    private long lastAcceptedSequence;
    private PreparationInput latest;
    private boolean open;

    public synchronized void open(long roundNumber) {
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        this.roundNumber = roundNumber;
        lastAcceptedSequence = 0L;
        latest = null;
        open = true;
    }

    public synchronized OfferResult offer(PreparationInput input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (!open) {
            return OfferResult.NOT_OPEN;
        }
        if (input.roundNumber() != roundNumber) {
            return OfferResult.ROUND_MISMATCH;
        }
        if (input.sequence() <= lastAcceptedSequence) {
            return OfferResult.NON_MONOTONIC_SEQUENCE;
        }
        lastAcceptedSequence = input.sequence();
        latest = input;
        return OfferResult.ACCEPTED;
    }

    public synchronized Optional<PreparationInput> drainLatest() {
        PreparationInput drained = latest;
        latest = null;
        return Optional.ofNullable(drained);
    }

    public synchronized long lastAcceptedSequence() {
        return lastAcceptedSequence;
    }

    public synchronized void close() {
        open = false;
        latest = null;
        roundNumber = 0L;
        lastAcceptedSequence = 0L;
    }

    public enum OfferResult {
        ACCEPTED,
        NOT_OPEN,
        ROUND_MISMATCH,
        NON_MONOTONIC_SEQUENCE
    }
}
