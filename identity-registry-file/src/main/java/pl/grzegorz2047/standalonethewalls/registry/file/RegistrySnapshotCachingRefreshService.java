package pl.grzegorz2047.standalonethewalls.registry.file;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import pl.grzegorz2047.standalonethewalls.registry.RegistryActivationResult;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshotCommit;

/** Verifies one provider artifact, atomically caches it, and only then publishes activation. */
public final class RegistrySnapshotCachingRefreshService {
    private final RegistrySnapshotService snapshotService;
    private final VerifiedRegistrySnapshotCommit commit;

    public RegistrySnapshotCachingRefreshService(
            RegistrySnapshotService snapshotService, RegistrySnapshotBundleFile bundleFile) {
        this(snapshotService, Objects.requireNonNull(bundleFile, "bundleFile")::storeVerified);
    }

    RegistrySnapshotCachingRefreshService(
            RegistrySnapshotService snapshotService, VerifiedRegistrySnapshotCommit commit) {
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.commit = Objects.requireNonNull(commit, "commit");
    }

    public RegistryActivationResult refresh(RegistrySnapshotProvider provider)
            throws RegistrySnapshotProviderException, RegistrySnapshotException {
        return snapshotService.refreshAndCommit(
                Objects.requireNonNull(provider, "provider"), commit);
    }

    public Outcome refreshClassified(RegistrySnapshotProvider provider)
            throws RegistrySnapshotException {
        AtomicBoolean cacheCommitStarted = new AtomicBoolean();
        try {
            RegistryActivationResult activation =
                    snapshotService.refreshAndCommit(
                            Objects.requireNonNull(provider, "provider"),
                            (artifact, verifiedSnapshot) -> {
                                cacheCommitStarted.set(true);
                                commit.commit(artifact, verifiedSnapshot);
                            });
            return activation == RegistryActivationResult.ACTIVATED
                    ? Outcome.ACTIVATED
                    : Outcome.UNCHANGED;
        } catch (RegistrySnapshotProviderException exception) {
            return cacheCommitStarted.get() ? Outcome.CACHE_FAILURE : Outcome.PROVIDER_FAILURE;
        }
    }

    public enum Outcome {
        ACTIVATED,
        UNCHANGED,
        PROVIDER_FAILURE,
        CACHE_FAILURE
    }
}
