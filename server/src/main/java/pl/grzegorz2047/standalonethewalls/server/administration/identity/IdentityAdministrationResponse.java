package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBan;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Closed, immutable response model for local identity administration adapters. */
public sealed interface IdentityAdministrationResponse {
    IdentityAdministrationResponseCode code();

    record PermissionDenied(IdentityAdministrationPermission requiredPermission)
            implements IdentityAdministrationResponse {
        public PermissionDenied {
            requiredPermission =
                    Objects.requireNonNull(requiredPermission, "requiredPermission");
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.PERMISSION_DENIED;
        }
    }

    record Handles(List<LocalHandleBinding> bindings) implements IdentityAdministrationResponse {
        public Handles {
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.HANDLES_LISTED;
        }
    }

    record Bans(List<LocalPlayerBan> bans) implements IdentityAdministrationResponse {
        public Bans {
            bans = List.copyOf(Objects.requireNonNull(bans, "bans"));
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.BANS_LISTED;
        }
    }

    record HandleInspection(CanonicalHandle handle, Optional<PlayerId> playerId)
            implements IdentityAdministrationResponse {
        public HandleInspection {
            handle = Objects.requireNonNull(handle, "handle");
            playerId = Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.HANDLE_INSPECTED;
        }
    }

    record BanInspection(PlayerId playerId, Optional<LocalPlayerBan> ban)
            implements IdentityAdministrationResponse {
        public BanInspection {
            playerId = Objects.requireNonNull(playerId, "playerId");
            ban = Objects.requireNonNull(ban, "ban");
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.BAN_INSPECTED;
        }
    }

    record HandleMutation(LocalHandleAdministrationResult result)
            implements IdentityAdministrationResponse {
        public HandleMutation {
            result = Objects.requireNonNull(result, "result");
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.HANDLE_MUTATION_COMPLETED;
        }
    }

    record BanMutation(LocalPlayerBanAdministrationResult result)
            implements IdentityAdministrationResponse {
        public BanMutation {
            result = Objects.requireNonNull(result, "result");
        }

        @Override
        public IdentityAdministrationResponseCode code() {
            return IdentityAdministrationResponseCode.BAN_MUTATION_COMPLETED;
        }
    }
}
