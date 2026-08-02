package pl.grzegorz2047.standalonethewalls.registry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;

/** Immutable snapshot after canonical bytes, digest, signature, schema and policy verification. */
public final class VerifiedRegistrySnapshot {
    private final long sequence;
    private final Instant generatedAt;
    private final RegistryRootId rootKeyId;
    private final byte[] digest;
    private final byte[] signature;
    private final List<RegistrySnapshotEntry> entries;
    private final Map<String, RegistrySnapshotEntry> entriesByHandle;

    VerifiedRegistrySnapshot(RegistrySnapshotPayload payload, byte[] digest, byte[] signature) {
        Objects.requireNonNull(payload, "payload");
        this.sequence = payload.sequence();
        this.generatedAt = payload.generatedAt();
        this.rootKeyId = payload.rootKeyId();
        this.digest = Objects.requireNonNull(digest, "digest").clone();
        this.signature = Objects.requireNonNull(signature, "signature").clone();
        this.entries = List.copyOf(payload.entries());
        Map<String, RegistrySnapshotEntry> indexed = new LinkedHashMap<>();
        for (RegistrySnapshotEntry entry : entries) {
            indexed.put(entry.handle().value(), entry);
        }
        this.entriesByHandle = Map.copyOf(indexed);
    }

    public long sequence() {
        return sequence;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public RegistryRootId rootKeyId() {
        return rootKeyId;
    }

    public byte[] digest() {
        return digest.clone();
    }

    public List<RegistrySnapshotEntry> entries() {
        return List.copyOf(entries);
    }

    public Optional<RegistrySnapshotEntry> find(CanonicalHandle handle) {
        return Optional.ofNullable(
                entriesByHandle.get(Objects.requireNonNull(handle, "handle").value()));
    }

    public int size() {
        return entries.size();
    }

    /** Returns true only for the exact detached artifact represented by this verified snapshot. */
    public boolean matchesArtifact(RegistrySnapshotArtifact artifact) {
        RegistrySnapshotArtifact candidate = Objects.requireNonNull(artifact, "artifact");
        if (!MessageDigest.isEqual(digest, candidate.digest())
                || !MessageDigest.isEqual(signature, candidate.signature())) {
            return false;
        }
        return MessageDigest.isEqual(digest, sha256(candidate.canonicalJson()));
    }

    @Override
    public String toString() {
        return "VerifiedRegistrySnapshot[sequence="
                + sequence
                + ", generatedAt="
                + generatedAt
                + ", rootKeyId="
                + rootKeyId
                + ", digestBytes="
                + digest.length
                + ", signatureBytes="
                + signature.length
                + ", entries="
                + entries.size()
                + ']';
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
