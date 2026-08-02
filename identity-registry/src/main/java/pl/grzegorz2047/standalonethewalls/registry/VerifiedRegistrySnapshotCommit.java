package pl.grzegorz2047.standalonethewalls.registry;

/**
 * Persists or otherwise commits the exact artifact corresponding to a verified snapshot before that
 * snapshot becomes visible as active.
 */
@FunctionalInterface
public interface VerifiedRegistrySnapshotCommit {
    void commit(RegistrySnapshotArtifact artifact, VerifiedRegistrySnapshot verifiedSnapshot)
            throws RegistrySnapshotProviderException;
}
