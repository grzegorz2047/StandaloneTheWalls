package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;

class PreparationInputMailboxTest {
    @Test
    void keepsOnlyTheNewestMonotonicInputForTheOpenRound() {
        PreparationInputMailbox mailbox = new PreparationInputMailbox();
        mailbox.open(3L);
        PreparationInput first = new PreparationInput(3L, 1L, 127, 0, 0, 0);
        PreparationInput newest = new PreparationInput(3L, 2L, 0, -127, 9_000, -500);

        assertThat(mailbox.offer(first))
                .isEqualTo(PreparationInputMailbox.OfferResult.ACCEPTED);
        assertThat(mailbox.offer(newest))
                .isEqualTo(PreparationInputMailbox.OfferResult.ACCEPTED);
        assertThat(mailbox.lastAcceptedSequence()).isEqualTo(2L);
        assertThat(mailbox.drainLatest()).contains(newest);
        assertThat(mailbox.drainLatest()).isEmpty();
    }

    @Test
    void rejectsClosedWrongRoundAndNonMonotonicInputWithoutGrowingState() {
        PreparationInputMailbox mailbox = new PreparationInputMailbox();
        PreparationInput input = new PreparationInput(1L, 1L, 0, 0, 0, 0);

        assertThat(mailbox.offer(input))
                .isEqualTo(PreparationInputMailbox.OfferResult.NOT_OPEN);
        mailbox.open(2L);
        assertThat(mailbox.offer(input))
                .isEqualTo(PreparationInputMailbox.OfferResult.ROUND_MISMATCH);
        assertThat(mailbox.offer(new PreparationInput(2L, 2L, 0, 0, 0, 0)))
                .isEqualTo(PreparationInputMailbox.OfferResult.ACCEPTED);
        assertThat(mailbox.offer(new PreparationInput(2L, 2L, 127, 0, 0, 0)))
                .isEqualTo(PreparationInputMailbox.OfferResult.NON_MONOTONIC_SEQUENCE);
        assertThat(mailbox.offer(new PreparationInput(2L, 1L, 127, 0, 0, 0)))
                .isEqualTo(PreparationInputMailbox.OfferResult.NON_MONOTONIC_SEQUENCE);
        assertThat(mailbox.drainLatest()).contains(new PreparationInput(2L, 2L, 0, 0, 0, 0));

        mailbox.close();
        assertThat(mailbox.offer(new PreparationInput(2L, 3L, 0, 0, 0, 0)))
                .isEqualTo(PreparationInputMailbox.OfferResult.NOT_OPEN);
        assertThat(mailbox.drainLatest()).isEmpty();
    }
}
