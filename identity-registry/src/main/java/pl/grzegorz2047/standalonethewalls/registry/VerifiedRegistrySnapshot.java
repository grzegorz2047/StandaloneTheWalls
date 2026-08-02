package pl.grzegorz2047.standalonethewalls.registry;

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
    private final List<RegistrySnapshotEntry> entries;
    private final Map<String, RegistrySnapshotEntry> entriesByHandle;

    VerifiedRegistrySnapshot(RegistrySnapshotPayload payload, byte[] digest) {
        Objects.requireNonNull(payload, "payload");
        this.sequence = payload.sequence();
        this.generatedAt = payload.generatedAt();
        this.rootKeyId = payload.rootKeyId();
        this.digest = Objects.requireNonNull(digest, "digest").clone();
        this.entries = payload.entries();
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
        return entries;
    }

    public Optional<RegistrySnapshotEntry> find(CanonicalHandle handle) {
        return Optional.ofNullable(
                entriesByHandle.get(Objects.requireNonNull(handle, "handle").value()));
    }

    public int size() {
        return entries.size();
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
                + ", entries="
                + entries.size()
                + ']';
    }
}
