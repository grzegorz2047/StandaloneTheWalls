package pl.grzegorz2047.standalonethewalls.server.identity;

import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;

/** Internal construction boundary that keeps process validation and runtime tests network-free. */
@FunctionalInterface
interface RegistryRefreshProviderFactory {
    RegistrySnapshotProvider create(RegistrySnapshotHttpsConfiguration configuration);
}
