package pl.grzegorz2047.standalonethewalls.registry;

/** Replaceable source boundary; providers supply bytes but never establish trust. */
@FunctionalInterface
public interface RegistrySnapshotProvider {
    RegistrySnapshotArtifact load() throws RegistrySnapshotProviderException;
}
