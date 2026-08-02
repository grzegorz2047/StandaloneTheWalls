package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengeDiscardTest {
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "a".repeat(52));
    private static final SecureChannelBinding CHANNEL_BINDING =
            new SecureChannelBinding(new byte[SecureChannelBinding.BYTES]);

    @Test
    void discardRemovesOnlyTheOutstandingSessionChallenge() {
        IdentityChallengeService service =
                new IdentityChallengeService(
                        new ChallengeLedger(
                                Clock.systemUTC(), new SecureRandom(), Duration.ofSeconds(30), 4));
        UUID first = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID second = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        service.issue(SERVER_ID, first, CHANNEL_BINDING);
        service.issue(SERVER_ID, second, CHANNEL_BINDING);

        assertThat(service.discard(first)).isTrue();
        assertThat(service.discard(first)).isFalse();
        assertThat(service.outstandingCount()).isEqualTo(1);
        assertThat(service.verify(first, placeholderProof()).status())
                .isEqualTo(IdentityVerification.Status.MISSING_CHALLENGE);
        assertThat(service.discard(second)).isTrue();
        assertThat(service.outstandingCount()).isZero();
    }

    private static IdentityProof placeholderProof() {
        byte[] publicKey =
                java.util.Base64.getDecoder()
                        .decode("MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=");
        return new IdentityProof(
                pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion.CURRENT,
                new CanonicalHandle("player_one"),
                new PlayerId("sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua"),
                publicKey,
                new byte[64]);
    }
}
