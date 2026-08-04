package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;

class RealtimeTicketValuesTest {
    @Test
    void contextRejectsZeroSessionNegativeEpochAndWrongDigestLength() {
        ServerId serverId = new ServerId("sfs1_" + "a".repeat(52));
        PlayerId playerId = new PlayerId("sf1_" + "b".repeat(52));
        RealtimeChannelBindingDigest digest = new RealtimeChannelBindingDigest(filled(32, 1));

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new RealtimeTicketContext(
                                        serverId, new UUID(0L, 0L), playerId, digest, 0L));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new RealtimeTicketContext(
                                        serverId, UUID.randomUUID(), playerId, digest, -1L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealtimeChannelBindingDigest(new byte[31]));
    }

    @Test
    void identitiesAndDigestsUseDefensiveCopiesAndValueEquality() {
        byte[] identityBytes = filled(16, 2);
        byte[] digestBytes = filled(32, 3);
        RealtimeTicketIdentity identity = new RealtimeTicketIdentity(identityBytes);
        RealtimeChannelBindingDigest digest = new RealtimeChannelBindingDigest(digestBytes);
        identityBytes[0] = 99;
        digestBytes[0] = 99;

        assertThat(identity).isEqualTo(new RealtimeTicketIdentity(filled(16, 2)));
        assertThat(digest).isEqualTo(new RealtimeChannelBindingDigest(filled(32, 3)));
        assertThat(identity.copyBytes()).containsOnly(2);
        assertThat(digest.copyBytes()).containsOnly(3);
        assertThat(identity.toString()).doesNotContain("0202");
        assertThat(digest.toString()).doesNotContain("0303");
    }

    @Test
    void preSharedKeyIsDefensivelyCopiedRedactedAndDestroyable() {
        byte[] source = filled(32, 4);
        RealtimePreSharedKey key = new RealtimePreSharedKey(source);
        source[0] = 99;
        byte[] firstCopy = key.copyBytes();
        firstCopy[0] = 99;

        assertThat(key.copyBytes()).containsOnly(4);
        assertThat(key.toString()).contains("redacted").doesNotContain("0404");

        key.close();
        key.close();

        assertThat(key.isDestroyed()).isTrue();
        assertThatIllegalStateException().isThrownBy(key::copyBytes);
    }

    @Test
    void configurationEnforcesHardBounds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealtimeTicketStoreConfig(0, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new RealtimeTicketStoreConfig(
                                        RealtimeTicketStoreConfig.HARD_MAXIMUM_ACTIVE_TICKETS + 1,
                                        Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealtimeTicketStoreConfig(1, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new RealtimeTicketStoreConfig(
                                        1,
                                        RealtimeTicketStoreConfig.HARD_MAXIMUM_LIFETIME.plusNanos(
                                                1)));
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
