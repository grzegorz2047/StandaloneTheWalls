package pl.grzegorz2047.standalonethewalls.server.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionDecision;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;

/**
 * Composes player-ban and canonical-handle policy after cryptographic authentication and before
 * lobby admission.
 */
public final class SessionIdentityAdmissionService {
    private final PlayerBanAdmissionService playerBanAdmission;
    private final HandleAuthorizationService handleAuthorization;

    public SessionIdentityAdmissionService(
            PlayerBanAdmissionService playerBanAdmission,
            HandleAuthorizationService handleAuthorization) {
        this.playerBanAdmission = Objects.requireNonNull(playerBanAdmission, "playerBanAdmission");
        this.handleAuthorization =
                Objects.requireNonNull(handleAuthorization, "handleAuthorization");
    }

    public SessionIdentityAdmissionDecision evaluate(
            HandleAuthorizationMode mode,
            CanonicalHandle handle,
            PlayerId playerId,
            RegistrySnapshotAvailability registryAvailability) {
        HandleAuthorizationMode selectedMode = Objects.requireNonNull(mode, "mode");
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        RegistrySnapshotAvailability availability =
                Objects.requireNonNull(registryAvailability, "registryAvailability");

        if (playerBanAdmission.evaluate(identity) == PlayerBanAdmissionDecision.PLAYER_BANNED) {
            return SessionIdentityAdmissionDecision.PLAYER_BANNED;
        }
        return SessionIdentityAdmissionDecision.fromHandle(
                handleAuthorization.authorize(
                        selectedMode, canonicalHandle, identity, availability));
    }
}
