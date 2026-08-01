package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/** Stateless verification of one proof against one consumed server challenge. */
public final class IdentityAuthenticator {
    private IdentityAuthenticator() {
        throw new AssertionError("No instances");
    }

    public static IdentityVerification verify(
            IdentityChallenge challenge, IdentityProof proof) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(proof, "proof");

        if (!proof.protocolVersion().equals(ProtocolVersion.CURRENT)) {
            return IdentityVerification.rejected(
                    IdentityVerification.Status.UNSUPPORTED_VERSION);
        }

        byte[] publicKeyBytes = proof.publicKey();
        PublicKey publicKey;
        PlayerId derived;
        try {
            publicKey = IdentityKeys.decodePublicKey(publicKeyBytes);
            derived = PlayerId.fromPublicKey(publicKeyBytes);
        } catch (IdentityException exception) {
            return IdentityVerification.rejected(
                    IdentityVerification.Status.INVALID_PUBLIC_KEY);
        }
        if (!derived.equals(proof.playerId())) {
            return IdentityVerification.rejected(
                    IdentityVerification.Status.PLAYER_ID_MISMATCH);
        }

        byte[] transcript = IdentityTranscript.encode(
                proof.protocolVersion(),
                challenge,
                proof.handle(),
                proof.playerId(),
                publicKeyBytes);
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(transcript);
            if (!verifier.verify(proof.signature())) {
                return IdentityVerification.rejected(
                        IdentityVerification.Status.INVALID_SIGNATURE);
            }
            return IdentityVerification.accepted(proof.playerId(), proof.handle());
        } catch (SignatureException exception) {
            return IdentityVerification.rejected(
                    IdentityVerification.Status.INVALID_SIGNATURE);
        } catch (GeneralSecurityException exception) {
            return IdentityVerification.rejected(
                    IdentityVerification.Status.CRYPTOGRAPHY_FAILURE);
        } finally {
            Arrays.fill(transcript, (byte) 0);
        }
    }
}
