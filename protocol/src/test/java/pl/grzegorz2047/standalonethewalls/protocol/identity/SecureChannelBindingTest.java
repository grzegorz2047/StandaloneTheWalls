package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecureChannelBindingTest {
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "a".repeat(52));
    private static final UUID SESSION_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void requiresExactlyThirtyTwoBytesAndCopiesAtBothBoundaries() {
        byte[] source = new byte[SecureChannelBinding.BYTES];
        source[0] = 7;
        SecureChannelBinding binding = new SecureChannelBinding(source);
        source[0] = 99;

        byte[] returned = binding.bytes();
        returned[0] = 1;

        byte[] expected = new byte[SecureChannelBinding.BYTES];
        expected[0] = 7;
        assertArrayEquals(expected, binding.bytes());
        assertEquals(new SecureChannelBinding(expected), binding);
        assertEquals(new SecureChannelBinding(expected).hashCode(), binding.hashCode());
        assertNotEquals(new SecureChannelBinding(new byte[SecureChannelBinding.BYTES]), binding);
        assertThrows(IllegalArgumentException.class, () -> new SecureChannelBinding(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> new SecureChannelBinding(new byte[33]));
    }

    @Test
    void missingBindingFailsBeforeAChallengeCanBeCreated() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new IdentityChallenge(
                                SERVER_ID,
                                SESSION_ID,
                                new byte[IdentityChallenge.NONCE_BYTES],
                                null,
                                Instant.parse("2026-08-01T17:01:00Z")));
    }

    @Test
    void neverPrintsBindingOrNonceBytes() {
        byte[] secret = new byte[SecureChannelBinding.BYTES];
        java.util.Arrays.fill(secret, (byte) 0x5A);
        SecureChannelBinding binding = new SecureChannelBinding(secret);
        IdentityChallenge challenge =
                new IdentityChallenge(
                        SERVER_ID,
                        SESSION_ID,
                        secret,
                        binding,
                        Instant.parse("2026-08-01T17:01:00Z"));

        assertEquals("SecureChannelBinding[bytes=32]", binding.toString());
        assertFalse(binding.toString().contains("90"));
        assertFalse(challenge.toString().contains("90"));
    }
}
