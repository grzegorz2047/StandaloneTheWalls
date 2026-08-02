package pl.grzegorz2047.standalonethewalls.registry.file;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistryActivationResult;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;

/** Verifies one provider artifact, atomically caches it, and only then publishes activation. */
public final class RegistrySnapshotCachingRefreshService {
    private final RegistrySnapshotService snapshotService;
    private final RegistrySnapshotBundleFile bundleFile;

    public RegistrySnapshotCachingRefreshService(
            RegistrySnapshotService snapshotService, RegistrySnapshotBundleFile bundleFile) {
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.bundleFile = Objects.requireNonNull(bundleFile, "bundleFile");
    }

    public RegistryActivationResult refresh(RegistrySnapshotProvider provider)
            throws RegistrySnapshotProviderException, RegistrySnapshotException {
        return snapshotService.refreshAndCommit(
                Objects.requireNonNull(provider, "provider"), bundleFile::storeVerified);
    }
}
