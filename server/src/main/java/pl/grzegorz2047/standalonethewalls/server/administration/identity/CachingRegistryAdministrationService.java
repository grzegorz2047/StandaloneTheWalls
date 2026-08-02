package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.AtomicRegistrySnapshotStore;
import pl.grzegorz2047.standalonethewalls.registry.RegistryActivationResult;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotCachingRefreshService;

/** Maps a remote verified cache-before-activation workflow to stable administration results. */
public final class CachingRegistryAdministrationService
        implements RegistryAdministrationOperations {
    private final RegistrySnapshotService snapshots;
    private final RegistrySnapshotProvider provider;
    private final RegistrySnapshotCachingRefreshService refresh;
    private final AtomicRegistrySnapshotStore store;

    public CachingRegistryAdministrationService(
            RegistrySnapshotService snapshots,
            RegistrySnapshotProvider provider,
            RegistrySnapshotCachingRefreshService refresh,
            AtomicRegistrySnapshotStore store) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public synchronized RegistryAdministrationResult verifySnapshot() {
        try {
            return RegistryAdministrationResult.verified(
                    RegistrySnapshotSummary.from(snapshots.verify(provider)));
        } catch (RegistrySnapshotProviderException exception) {
            return RegistryAdministrationResult.providerFailure();
        } catch (RegistrySnapshotException exception) {
            return RegistryAdministrationResult.snapshotRejected(exception.code());
        }
    }

    @Override
    public synchronized RegistryAdministrationResult reloadRegistry() {
        try {
            RegistryActivationResult activation = refresh.refresh(provider);
            VerifiedRegistrySnapshot active =
                    store.active()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "successful registry refresh did not publish active state"));
            RegistrySnapshotSummary summary = RegistrySnapshotSummary.from(active);
            return activation == RegistryActivationResult.ACTIVATED
                    ? RegistryAdministrationResult.activated(summary)
                    : RegistryAdministrationResult.unchanged(summary);
        } catch (RegistrySnapshotProviderException exception) {
            return RegistryAdministrationResult.providerFailure();
        } catch (RegistrySnapshotException exception) {
            return RegistryAdministrationResult.snapshotRejected(exception.code());
        }
    }

    public synchronized AutomaticRegistryRefreshResult refreshAutomatically() {
        try {
            return switch (refresh.refreshClassified(provider)) {
                case ACTIVATED -> AutomaticRegistryRefreshResult.ACTIVATED;
                case UNCHANGED -> AutomaticRegistryRefreshResult.UNCHANGED;
                case PROVIDER_FAILURE -> AutomaticRegistryRefreshResult.PROVIDER_FAILURE;
                case CACHE_FAILURE -> AutomaticRegistryRefreshResult.CACHE_FAILURE;
            };
        } catch (RegistrySnapshotException exception) {
            return switch (exception.code()) {
                case ROLLBACK -> AutomaticRegistryRefreshResult.ROLLBACK_REJECTED;
                case EQUIVOCATION -> AutomaticRegistryRefreshResult.EQUIVOCATION_REJECTED;
                default -> AutomaticRegistryRefreshResult.SNAPSHOT_REJECTED;
            };
        }
    }
}
