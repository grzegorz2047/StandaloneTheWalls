package pl.grzegorz2047.standalonethewalls.registry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parsed snapshot content before detached digest and signature verification. */
public final class RegistrySnapshotPayload {
    public static final int SCHEMA_VERSION = 1;

    private final long sequence;
    private final Instant generatedAt;
    private final RegistryRootId rootKeyId;
    private final List<RegistrySnapshotEntry> entries;

    public RegistrySnapshotPayload(
            long sequence,
            Instant generatedAt,
            RegistryRootId rootKeyId,
            List<RegistrySnapshotEntry> entries)
            throws RegistrySnapshotException {
        if (sequence < 0L) {
            throw new RegistrySnapshotException(
                    RegistrySnapshotException.Code.INVALID_SEQUENCE,
                    "registry snapshot sequence cannot be negative");
        }
        this.sequence = sequence;
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        this.rootKeyId = Objects.requireNonNull(rootKeyId, "rootKeyId");
        this.entries = List.copyOf(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
        requireStrictOrder(this.entries);
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

    public List<RegistrySnapshotEntry> entries() {
        return entries;
    }

    private static void requireStrictOrder(List<RegistrySnapshotEntry> entries)
            throws RegistrySnapshotException {
        String previous = null;
        for (RegistrySnapshotEntry entry : entries) {
            String current = Objects.requireNonNull(entry, "entry").handle().value();
            if (previous != null) {
                int comparison = previous.compareTo(current);
                if (comparison == 0) {
                    throw new RegistrySnapshotException(
                            RegistrySnapshotException.Code.DUPLICATE_HANDLE,
                            "registry snapshot contains a duplicate handle");
                }
                if (comparison > 0) {
                    throw new RegistrySnapshotException(
                            RegistrySnapshotException.Code.UNSORTED_ENTRIES,
                            "registry snapshot entries are not strictly sorted");
                }
            }
            previous = current;
        }
    }
}
