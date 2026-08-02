package pl.grzegorz2047.standalonethewalls.registry;

import java.util.Objects;

/** Loads, verifies and atomically activates snapshots without trusting the provider. */
public final class RegistrySnapshotService {
    private final RegistrySnapshotVerifier verifier;
    private final RegistryTrustBundle trustBundle;
    private final RegistrySnapshotPolicy policy;
    private final AtomicRegistrySnapshotStore store;

    public RegistrySnapshotService(
            RegistrySnapshotVerifier verifier,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy policy,
            AtomicRegistrySnapshotStore store) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.trustBundle = Objects.requireNonNull(trustBundle, "trustBundle");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.store = Objects.requireNonNull(store, "store");
    }

    public RegistryActivationResult refresh(RegistrySnapshotProvider provider)
            throws RegistrySnapshotProviderException, RegistrySnapshotException {
        RegistrySnapshotArtifact artifact = Objects.requireNonNull(provider, "provider").load();
        VerifiedRegistrySnapshot verified = verifier.verify(artifact, trustBundle, policy);
        return store.activate(verified);
    }
}
