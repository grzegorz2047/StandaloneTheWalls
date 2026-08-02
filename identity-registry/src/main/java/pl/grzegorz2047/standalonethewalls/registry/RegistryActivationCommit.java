package pl.grzegorz2047.standalonethewalls.registry;

/** Checked side effect that must complete before a verified snapshot becomes active. */
@FunctionalInterface
interface RegistryActivationCommit {
    void commit() throws RegistrySnapshotProviderException;
}
