package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

class IdentityAuthenticatorTest {
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final CanonicalHandle HANDLE = new CanonicalHandle("grzegorz2047");

    @Test
    void acceptsAProofBoundToTheExactServerSessionNonceHandleAndPlayerId()
            throws IdentityException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge challenge = challenge("server.eu-1", SESSION, nonce(1));
        IdentityProof proof =
                IdentityProof.create(identity, ProtocolVersion.CURRENT, challenge, HANDLE);

        IdentityVerification verification = IdentityAuthenticator.verify(challenge, proof);

        assertTrue(verification.isAccepted());
        assertEquals(identity.playerId(), verification.playerId().orElseThrow());
        assertEquals(HANDLE, verification.handle().orElseThrow());
    }

    @Test
    void rejectsAProofRelayedToAnotherServerSessionOrNonce() throws IdentityException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge original = challenge("server.eu-1", SESSION, nonce(1));
        IdentityProof proof =
                IdentityProof.create(identity, ProtocolVersion.CURRENT, original, HANDLE);

        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                IdentityAuthenticator.verify(challenge("server.eu-2", SESSION, nonce(1)), proof)
                        .status());
        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                IdentityAuthenticator.verify(
                                challenge(
                                        "server.eu-1",
                                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                                        nonce(1)),
                                proof)
                        .status());
        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                IdentityAuthenticator.verify(challenge("server.eu-1", SESSION, nonce(2)), proof)
                        .status());
    }

    @Test
    void rejectsTamperedHandlePlayerIdPublicKeySignatureAndVersion()
            throws IdentityException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        PlayerIdentity other = PlayerIdentity.generate(new SecureRandom());
        IdentityChallenge challenge = challenge("server.eu-1", SESSION, nonce(1));
        IdentityProof proof =
                IdentityProof.create(identity, ProtocolVersion.CURRENT, challenge, HANDLE);

        IdentityProof changedHandle =
                new IdentityProof(
                        proof.protocolVersion(),
                        new CanonicalHandle("another_player"),
                        proof.playerId(),
                        proof.publicKey(),
                        proof.signature());
        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                IdentityAuthenticator.verify(challenge, changedHandle).status());

        IdentityProof changedId =
                new IdentityProof(
                        proof.protocolVersion(),
                        HANDLE,
                        other.playerId(),
                        proof.publicKey(),
                        proof.signature());
        assertEquals(
                IdentityVerification.Status.PLAYER_ID_MISMATCH,
                IdentityAuthenticator.verify(challenge, changedId).status());

        IdentityProof badKey =
                new IdentityProof(
                        proof.protocolVersion(),
                        HANDLE,
                        proof.playerId(),
                        new byte[] {1, 2, 3},
                        proof.signature());
        assertEquals(
                IdentityVerification.Status.INVALID_PUBLIC_KEY,
                IdentityAuthenticator.verify(challenge, badKey).status());

        byte[] signature = proof.signature();
        signature[0] ^= 1;
        IdentityProof badSignature =
                new IdentityProof(
                        proof.protocolVersion(),
                        HANDLE,
                        proof.playerId(),
                        proof.publicKey(),
                        signature);
        assertEquals(
                IdentityVerification.Status.INVALID_SIGNATURE,
                IdentityAuthenticator.verify(challenge, badSignature).status());

        IdentityProof unsupported =
                new IdentityProof(
                        new ProtocolVersion(1, 1),
                        HANDLE,
                        proof.playerId(),
                        proof.publicKey(),
                        proof.signature());
        IdentityVerification unsupportedResult =
                IdentityAuthenticator.verify(challenge, unsupported);
        assertFalse(unsupportedResult.isAccepted());
        assertEquals(IdentityVerification.Status.UNSUPPORTED_VERSION, unsupportedResult.status());
    }

    private static IdentityChallenge challenge(String serverId, UUID sessionId, byte[] nonce) {
        return new IdentityChallenge(
                serverId, sessionId, nonce, Instant.parse("2026-08-01T17:01:00Z"));
    }

    private static byte[] nonce(int seed) {
        byte[] value = new byte[IdentityChallenge.NONCE_BYTES];
        value[0] = (byte) seed;
        return value;
    }
}
