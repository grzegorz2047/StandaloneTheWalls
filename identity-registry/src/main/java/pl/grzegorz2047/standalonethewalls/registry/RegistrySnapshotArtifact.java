package pl.grzegorz2047.standalonethewalls.registry;

import java.util.Objects;

/** Exact JSON bytes with detached SHA-256 digest and Ed25519 registry-root signature. */
public final class RegistrySnapshotArtifact {
    public static final int DIGEST_BYTES = 32;
    public static final int SIGNATURE_BYTES = 64;

    private final byte[] canonicalJson;
    private final byte[] digest;
    private final byte[] signature;

    public RegistrySnapshotArtifact(byte[] canonicalJson, byte[] digest, byte[] signature) {
        this.canonicalJson = Objects.requireNonNull(canonicalJson, "canonicalJson").clone();
        this.digest = Objects.requireNonNull(digest, "digest").clone();
        this.signature = Objects.requireNonNull(signature, "signature").clone();
        if (this.canonicalJson.length == 0) {
            throw new IllegalArgumentException("registry snapshot JSON cannot be empty");
        }
        if (this.digest.length != DIGEST_BYTES) {
            throw new IllegalArgumentException("registry snapshot digest must contain 32 bytes");
        }
        if (this.signature.length != SIGNATURE_BYTES) {
            throw new IllegalArgumentException("registry snapshot signature must contain 64 bytes");
        }
    }

    public byte[] canonicalJson() {
        return canonicalJson.clone();
    }

    public byte[] digest() {
        return digest.clone();
    }

    public byte[] signature() {
        return signature.clone();
    }

    @Override
    public String toString() {
        return "RegistrySnapshotArtifact[jsonBytes="
                + canonicalJson.length
                + ", digestBytes="
                + digest.length
                + ", signatureBytes="
                + signature.length
                + ']';
    }
}
