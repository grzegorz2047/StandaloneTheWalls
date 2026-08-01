package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Arrays;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

/** Client proof sent in response to a challenge. */
public final class IdentityProof {
    private final ProtocolVersion protocolVersion;
    private final CanonicalHandle handle;
    private final PlayerId playerId;
    private final byte[] publicKey;
    private final byte[] signature;

    public IdentityProof(
            ProtocolVersion protocolVersion,
            CanonicalHandle handle,
            PlayerId playerId,
            byte[] publicKey,
            byte[] signature) {
        this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey").clone();
        this.signature = Objects.requireNonNull(signature, "signature").clone();
        if (publicKey.length == 0 || publicKey.length > 256) {
            throw new IllegalArgumentException("public key length is outside the allowed range");
        }
        if (signature.length != 64) {
            throw new IllegalArgumentException("Ed25519 signature must contain 64 bytes");
        }
    }

    public static IdentityProof create(
            PlayerIdentity identity,
            ProtocolVersion version,
            IdentityChallenge challenge,
            CanonicalHandle handle)
            throws IdentityException {
        Objects.requireNonNull(identity, "identity");
        byte[] publicKey = identity.publicKeyEncoded();
        byte[] transcript =
                IdentityTranscript.encode(
                        version, challenge, handle, identity.playerId(), publicKey);
        try {
            return new IdentityProof(
                    version, handle, identity.playerId(), publicKey, identity.sign(transcript));
        } finally {
            Arrays.fill(transcript, (byte) 0);
        }
    }

    public ProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    public CanonicalHandle handle() {
        return handle;
    }

    public PlayerId playerId() {
        return playerId;
    }

    public byte[] publicKey() {
        return publicKey.clone();
    }

    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public String toString() {
        return "IdentityProof[protocolVersion="
                + protocolVersion
                + ", handle="
                + handle
                + ", playerId="
                + playerId
                + ", publicKeyBytes="
                + publicKey.length
                + ", signatureBytes="
                + signature.length
                + ']';
    }
}
