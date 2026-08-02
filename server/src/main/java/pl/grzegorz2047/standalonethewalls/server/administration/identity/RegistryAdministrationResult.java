package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;

/** Immutable result that exposes bounded registry metadata and semantic failures only. */
public record RegistryAdministrationResult(
        RegistryAdministrationResultCode code,
        Optional<RegistrySnapshotSummary> snapshot,
        Optional<RegistrySnapshotException.Code> rejectionCode) {
    public RegistryAdministrationResult {
        code = Objects.requireNonNull(code, "code");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        rejectionCode = Objects.requireNonNull(rejectionCode, "rejectionCode");
        switch (code) {
            case VERIFIED, ACTIVATED, UNCHANGED -> {
                if (snapshot.isEmpty() || rejectionCode.isPresent()) {
                    throw new IllegalArgumentException(
                            "successful registry result requires only snapshot metadata");
                }
            }
            case PROVIDER_FAILURE -> {
                if (snapshot.isPresent() || rejectionCode.isPresent()) {
                    throw new IllegalArgumentException(
                            "provider failure cannot contain snapshot or rejection metadata");
                }
            }
            case SNAPSHOT_REJECTED -> {
                if (snapshot.isPresent() || rejectionCode.isEmpty()) {
                    throw new IllegalArgumentException(
                            "snapshot rejection requires only a semantic rejection code");
                }
            }
        }
    }

    public static RegistryAdministrationResult verified(RegistrySnapshotSummary snapshot) {
        return success(RegistryAdministrationResultCode.VERIFIED, snapshot);
    }

    public static RegistryAdministrationResult activated(RegistrySnapshotSummary snapshot) {
        return success(RegistryAdministrationResultCode.ACTIVATED, snapshot);
    }

    public static RegistryAdministrationResult unchanged(RegistrySnapshotSummary snapshot) {
        return success(RegistryAdministrationResultCode.UNCHANGED, snapshot);
    }

    public static RegistryAdministrationResult providerFailure() {
        return new RegistryAdministrationResult(
                RegistryAdministrationResultCode.PROVIDER_FAILURE,
                Optional.empty(),
                Optional.empty());
    }

    public static RegistryAdministrationResult snapshotRejected(
            RegistrySnapshotException.Code rejectionCode) {
        return new RegistryAdministrationResult(
                RegistryAdministrationResultCode.SNAPSHOT_REJECTED,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(rejectionCode, "rejectionCode")));
    }

    private static RegistryAdministrationResult success(
            RegistryAdministrationResultCode code, RegistrySnapshotSummary snapshot) {
        return new RegistryAdministrationResult(
                code, Optional.of(Objects.requireNonNull(snapshot, "snapshot")), Optional.empty());
    }
}
