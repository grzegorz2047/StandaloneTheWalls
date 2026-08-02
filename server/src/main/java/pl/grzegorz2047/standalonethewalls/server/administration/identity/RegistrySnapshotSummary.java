package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

/** Safe bounded registry metadata without canonical JSON or signature bytes. */
public record RegistrySnapshotSummary(
        long sequence, Instant generatedAt, RegistryRootId rootKeyId, String sha256, int entries) {
    public RegistrySnapshotSummary {
        if (sequence < 0L) {
            throw new IllegalArgumentException("registry sequence cannot be negative");
        }
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        rootKeyId = Objects.requireNonNull(rootKeyId, "rootKeyId");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        if (sha256.length() != 64
                || !sha256.chars()
                        .allMatch(
                                value ->
                                        (value >= '0' && value <= '9')
                                                || (value >= 'a' && value <= 'f'))) {
            throw new IllegalArgumentException("registry digest must be lowercase SHA-256 hex");
        }
        if (entries < 0) {
            throw new IllegalArgumentException("registry entry count cannot be negative");
        }
    }

    public static RegistrySnapshotSummary from(VerifiedRegistrySnapshot snapshot) {
        VerifiedRegistrySnapshot verified = Objects.requireNonNull(snapshot, "snapshot");
        return new RegistrySnapshotSummary(
                verified.sequence(),
                verified.generatedAt(),
                verified.rootKeyId(),
                HexFormat.of().formatHex(verified.digest()),
                verified.size());
    }
}
