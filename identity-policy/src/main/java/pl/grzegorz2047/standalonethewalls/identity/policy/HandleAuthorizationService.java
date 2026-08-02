package pl.grzegorz2047.standalonethewalls.identity.policy;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
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
        HandleAuthorizationMode selectedMode = Objects.requireNonNull(mode, "mode");
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        Optional<VerifiedRegistrySnapshot> snapshot =
                Objects.requireNonNull(activeSnapshot, "activeSnapshot");

        return switch (selectedMode) {
            case LOCAL_TOFU -> authorizeLocal(canonicalHandle, identity);
            case GLOBAL_ONLY ->
                    snapshot
                            .map(value -> authorizeGlobal(value, canonicalHandle, identity))
                            .orElse(HandleAuthorizationDecision.REGISTRY_UNAVAILABLE);
            case HYBRID ->
                    snapshot
                            .map(value -> authorizeHybrid(value, canonicalHandle, identity))
                            .orElse(HandleAuthorizationDecision.REGISTRY_UNAVAILABLE);
        };
    }

    private HandleAuthorizationDecision authorizeHybrid(
            VerifiedRegistrySnapshot snapshot, CanonicalHandle handle, PlayerId playerId) {
        return snapshot
                .find(handle)
                .map(entry -> authorizeGlobalEntry(entry, playerId))
                .orElseGet(() -> authorizeLocal(handle, playerId));
    }

    private static HandleAuthorizationDecision authorizeGlobal(
            VerifiedRegistrySnapshot snapshot, CanonicalHandle handle, PlayerId playerId) {
        return snapshot
                .find(handle)
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

    private HandleAuthorizationDecision authorizeLocal(
            CanonicalHandle handle, PlayerId playerId) {
        return switch (localBindings.bindOrVerify(handle, playerId)) {
            case BOUND -> HandleAuthorizationDecision.LOCAL_FIRST_USE_ACCEPTED;
            case MATCHED -> HandleAuthorizationDecision.LOCAL_RETURNING_ACCEPTED;
            case CONFLICT -> HandleAuthorizationDecision.LOCAL_BINDING_CONFLICT;
        };
    }
}
