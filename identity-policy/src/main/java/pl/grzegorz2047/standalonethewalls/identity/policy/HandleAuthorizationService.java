package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;

/** Pure authorization policy above cryptographic player authentication. */
public final class HandleAuthorizationService {
    private final LocalHandleBindingStore localBindings;

    public HandleAuthorizationService(LocalHandleBindingStore localBindings) {
        this.localBindings = Objects.requireNonNull(localBindings, "localBindings");
    }

    public HandleAuthorizationDecision authorize(
            HandleAuthorizationMode mode,
            CanonicalHandle handle,
            PlayerId playerId,
            Optional<VerifiedRegistrySnapshot> activeSnapshot) {
        Optional<VerifiedRegistrySnapshot> snapshot =
                Objects.requireNonNull(activeSnapshot, "activeSnapshot");
        RegistrySnapshotAvailability availability =
                snapshot.map(RegistrySnapshotAvailability::fresh)
                        .orElseGet(RegistrySnapshotAvailability::absent);
        return authorize(mode, handle, playerId, availability);
    }

    public HandleAuthorizationDecision authorize(
            HandleAuthorizationMode mode,
            CanonicalHandle handle,
            PlayerId playerId,
            RegistrySnapshotAvailability registryAvailability) {
        HandleAuthorizationMode selectedMode = Objects.requireNonNull(mode, "mode");
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        RegistrySnapshotAvailability availability =
                Objects.requireNonNull(registryAvailability, "registryAvailability");

        return switch (selectedMode) {
            case LOCAL_TOFU -> authorizeLocal(canonicalHandle, identity);
            case GLOBAL_ONLY -> authorizeGlobal(availability, canonicalHandle, identity);
            case HYBRID -> authorizeHybrid(availability, canonicalHandle, identity);
        };
    }

    private HandleAuthorizationDecision authorizeHybrid(
            RegistrySnapshotAvailability availability, CanonicalHandle handle, PlayerId playerId) {
        return switch (availability.state()) {
            case ABSENT -> HandleAuthorizationDecision.REGISTRY_UNAVAILABLE;
            case FRESH -> authorizeHybridFresh(availability.requireSnapshot(), handle, playerId);
            case STALE -> authorizeHybridStale(availability.requireSnapshot(), handle, playerId);
        };
    }

    private HandleAuthorizationDecision authorizeHybridFresh(
            VerifiedRegistrySnapshot snapshot, CanonicalHandle handle, PlayerId playerId) {
        return snapshot.find(handle)
                .map(entry -> authorizeGlobalEntry(entry, playerId))
                .orElseGet(() -> authorizeLocal(handle, playerId));
    }

    private HandleAuthorizationDecision authorizeHybridStale(
            VerifiedRegistrySnapshot snapshot, CanonicalHandle handle, PlayerId playerId) {
        return snapshot.find(handle).isPresent()
                ? HandleAuthorizationDecision.REGISTRY_STALE
                : authorizeLocal(handle, playerId);
    }

    private static HandleAuthorizationDecision authorizeGlobal(
            RegistrySnapshotAvailability availability, CanonicalHandle handle, PlayerId playerId) {
        return switch (availability.state()) {
            case ABSENT -> HandleAuthorizationDecision.REGISTRY_UNAVAILABLE;
            case STALE -> HandleAuthorizationDecision.REGISTRY_STALE;
            case FRESH -> authorizeGlobalFresh(availability.requireSnapshot(), handle, playerId);
        };
    }

    private static HandleAuthorizationDecision authorizeGlobalFresh(
            VerifiedRegistrySnapshot snapshot, CanonicalHandle handle, PlayerId playerId) {
        return snapshot.find(handle)
                .map(entry -> authorizeGlobalEntry(entry, playerId))
                .orElse(HandleAuthorizationDecision.UNKNOWN_GLOBAL_HANDLE);
    }

    private static HandleAuthorizationDecision authorizeGlobalEntry(
            RegistrySnapshotEntry entry, PlayerId playerId) {
        if (entry.status() == RegistryEntryStatus.REVOKED) {
            return HandleAuthorizationDecision.REVOKED_GLOBAL_HANDLE;
        }
        return entry.playerId().equals(playerId)
                ? HandleAuthorizationDecision.GLOBAL_ACCEPTED
                : HandleAuthorizationDecision.GLOBAL_PLAYER_MISMATCH;
    }

    private HandleAuthorizationDecision authorizeLocal(CanonicalHandle handle, PlayerId playerId) {
        return switch (localBindings.bindOrVerify(handle, playerId)) {
            case BOUND -> HandleAuthorizationDecision.LOCAL_FIRST_USE_ACCEPTED;
            case MATCHED -> HandleAuthorizationDecision.LOCAL_RETURNING_ACCEPTED;
            case CONFLICT -> HandleAuthorizationDecision.LOCAL_BINDING_CONFLICT;
        };
    }
}
