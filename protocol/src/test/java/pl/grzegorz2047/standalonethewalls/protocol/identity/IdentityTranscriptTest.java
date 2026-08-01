package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

class IdentityTranscriptTest {
    @Test
    void isDeterministicAndUnambiguousAcrossEveryBoundField() throws Exception {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        IdentityChallenge challenge =
                new IdentityChallenge(
                        "server.eu-1",
                        UUID.fromString("11111111-2222-3333-4444-555555555555"),
                        new byte[32],
                        Instant.parse("2026-08-01T17:01:00Z"));

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
                                new IdentityChallenge(
                                        "server.eu-2",
                                        challenge.sessionId(),
                                        challenge.nonce(),
                                        challenge.expiresAt()),
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
}
