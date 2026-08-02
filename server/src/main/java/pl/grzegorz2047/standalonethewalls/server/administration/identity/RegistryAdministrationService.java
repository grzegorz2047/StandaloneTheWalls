package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistryActivationResult;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

/** Maps provider and verification failures to stable local administration results. */
public final class RegistryAdministrationService implements RegistryAdministrationOperations {
    private final RegistrySnapshotService snapshots;
    private final RegistrySnapshotProvider provider;

    public RegistryAdministrationService(
            RegistrySnapshotService snapshots, RegistrySnapshotProvider provider) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public RegistryAdministrationResult verifySnapshot() {
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
    public RegistryAdministrationResult reloadRegistry() {
        try {
            VerifiedRegistrySnapshot verified = snapshots.verify(provider);
            RegistryActivationResult activation = snapshots.activate(verified);
            RegistrySnapshotSummary summary = RegistrySnapshotSummary.from(verified);
            return activation == RegistryActivationResult.ACTIVATED
                    ? RegistryAdministrationResult.activated(summary)
                    : RegistryAdministrationResult.unchanged(summary);
        } catch (RegistrySnapshotProviderException exception) {
            return RegistryAdministrationResult.providerFailure();
        } catch (RegistrySnapshotException exception) {
            return RegistryAdministrationResult.snapshotRejected(exception.code());
        }
    }
}
