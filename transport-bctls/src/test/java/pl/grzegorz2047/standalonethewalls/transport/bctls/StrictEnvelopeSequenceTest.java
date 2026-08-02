package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolException;

class StrictEnvelopeSequenceTest {
    @Test
    void claimsConsecutiveOutboundValues() throws ProtocolException {
        StrictEnvelopeSequence sequence = new StrictEnvelopeSequence();

        assertThat(sequence.claim()).isZero();
        assertThat(sequence.claim()).isEqualTo(1L);
    }

    @Test
    void acceptsOnlyTheExactNextInboundSequence() throws ProtocolException {
        StrictEnvelopeSequence sequence = new StrictEnvelopeSequence();

        sequence.accept(0L);
        sequence.accept(1L);

        assertThatThrownBy(() -> sequence.accept(1L))
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ProtocolException.Code.OUT_OF_ORDER_SEQUENCE));
    }

    @Test
    void rejectsAGapWithoutAdvancingTheExpectedValue() throws ProtocolException {
        StrictEnvelopeSequence sequence = new StrictEnvelopeSequence();

        assertThatThrownBy(() -> sequence.accept(1L)).isInstanceOf(ProtocolException.class);
        sequence.accept(0L);
    }

    @Test
    void marksTheSequenceSpaceExhaustedAfterLongMaximum() throws ProtocolException {
        StrictEnvelopeSequence sequence = new StrictEnvelopeSequence(Long.MAX_VALUE);

        assertThat(sequence.claim()).isEqualTo(Long.MAX_VALUE);

        assertThatThrownBy(sequence::claim)
                .isInstanceOfSatisfying(
                        ProtocolException.class,
                        exception ->
                                assertThat(exception.code())
                                        .isEqualTo(ProtocolException.Code.SEQUENCE_EXHAUSTED));
    }

    @Test
    void rejectsANegativeInitialExpectedValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StrictEnvelopeSequence(-1L));
    }
}
