package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

class IdentityTranscriptTest {
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "a".repeat(52));
    private static final byte[] PUBLIC_KEY_VECTOR =
            Base64.getDecoder()
                    .decode("MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=");

    @Test
    void isDeterministicAndUnambiguousAcrossEveryBoundField() throws IdentityException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        IdentityChallenge challenge = challenge(SERVER_ID, new byte[SecureChannelBinding.BYTES]);

        byte[] first =
                IdentityTranscript.encode(
                        ProtocolVersion.CURRENT,
                        challenge,
                        handle,
                        identity.playerId(),
                        identity.publicKeyEncoded());
        byte[] second =
                IdentityTranscript.encode(
                        ProtocolVersion.CURRENT,
                        challenge,
                        handle,
                        identity.playerId(),
                        identity.publicKeyEncoded());

        assertArrayEquals(first, second);
        assertFalse(
                Arrays.equals(
                        first,
                        IdentityTranscript.encode(
                                ProtocolVersion.CURRENT,
                                challenge(
                                        new ServerId("sfs1_" + "b".repeat(52)),
                                        challenge.channelBinding().bytes()),
                                handle,
                                identity.playerId(),
                                identity.publicKeyEncoded())));

        byte[] changedBinding = challenge.channelBinding().bytes();
        changedBinding[changedBinding.length - 1] = 1;
        assertFalse(
                Arrays.equals(
                        first,
                        IdentityTranscript.encode(
                                ProtocolVersion.CURRENT,
                                challenge(SERVER_ID, changedBinding),
                                handle,
                                identity.playerId(),
                                identity.publicKeyEncoded())));
        assertFalse(
                Arrays.equals(
                        first,
                        IdentityTranscript.encode(
                                ProtocolVersion.CURRENT,
                                challenge,
                                new CanonicalHandle("player_two"),
                                identity.playerId(),
                                identity.publicKeyEncoded())));
    }

    @Test
    void matchesThePublicV2TranscriptVector() throws IdentityException, NoSuchAlgorithmException {
        byte[] binding = new byte[SecureChannelBinding.BYTES];
        Arrays.fill(binding, (byte) 0x11);
        IdentityChallenge challenge =
                new IdentityChallenge(
                        ServerId.fromPublicKey(PUBLIC_KEY_VECTOR),
                        UUID.fromString("11111111-2222-3333-4444-555555555555"),
                        new byte[IdentityChallenge.NONCE_BYTES],
                        new SecureChannelBinding(binding),
                        Instant.EPOCH);

        byte[] transcript =
                IdentityTranscript.encode(
                        ProtocolVersion.CURRENT,
                        challenge,
                        new CanonicalHandle("player_one"),
                        PlayerId.fromPublicKey(PUBLIC_KEY_VECTOR),
                        PUBLIC_KEY_VECTOR);

        assertEquals(305, transcript.length);
        assertEquals(
                "94c807fe4c9db882fcaec58034722760009abf99063b13162690e102bbaf3e7b",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(transcript)));
    }

    private static IdentityChallenge challenge(ServerId serverId, byte[] binding) {
        return new IdentityChallenge(
                serverId,
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                new byte[IdentityChallenge.NONCE_BYTES],
                new SecureChannelBinding(binding),
                Instant.parse("2026-08-01T17:01:00Z"));
    }
}
