package pl.grzegorz2047.standalonethewalls.transport.bctls;

import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

/** Tracks an exact, gap-free non-negative sequence for one stream direction. */
final class StrictEnvelopeSequence {
    private long expected;
    private boolean exhausted;

    StrictEnvelopeSequence() {
        this(0L);
    }

    StrictEnvelopeSequence(long initialExpected) {
        if (initialExpected < 0L) {
            throw new IllegalArgumentException("initial expected sequence cannot be negative");
        }
        this.expected = initialExpected;
    }

    void accept(long actual) throws ProtocolException {
        if (exhausted) {
            throw new ProtocolException(
                    ProtocolException.Code.SEQUENCE_EXHAUSTED,
                    "the envelope sequence space is exhausted");
        }
        if (actual != expected) {
            throw new ProtocolException(
                    ProtocolException.Code.OUT_OF_ORDER_SEQUENCE,
                    "the envelope sequence is not the next expected value");
        }
        if (actual == Long.MAX_VALUE) {
            exhausted = true;
        } else {
            expected = actual + 1L;
        }
    }
}
