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

    long claim() throws ProtocolException {
        long claimed = expectedValue();
        advance(claimed);
        return claimed;
    }

    void accept(long actual) throws ProtocolException {
        long required = expectedValue();
        if (actual != required) {
            throw new ProtocolException(
                    ProtocolException.Code.OUT_OF_ORDER_SEQUENCE,
                    "the envelope sequence is not the next expected value");
        }
        advance(actual);
    }

    private long expectedValue() throws ProtocolException {
        if (exhausted) {
            throw new ProtocolException(
                    ProtocolException.Code.SEQUENCE_EXHAUSTED,
                    "the envelope sequence space is exhausted");
        }
        return expected;
    }

    private void advance(long accepted) {
        if (accepted == Long.MAX_VALUE) {
            exhausted = true;
        } else {
            expected = accepted + 1L;
        }
    }
}
