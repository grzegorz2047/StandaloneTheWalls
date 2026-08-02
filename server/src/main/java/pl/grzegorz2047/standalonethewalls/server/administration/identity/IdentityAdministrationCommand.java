package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Closed command model produced by a strict adapter parser. */
public sealed interface IdentityAdministrationCommand {
    record ListHandles() implements IdentityAdministrationCommand {}

    record ListBans() implements IdentityAdministrationCommand {}

    record VerifySnapshot() implements IdentityAdministrationCommand {}

    record ReloadRegistry() implements IdentityAdministrationCommand {}

    record InspectHandle(CanonicalHandle handle) implements IdentityAdministrationCommand {
        public InspectHandle {
            handle = Objects.requireNonNull(handle, "handle");
        }
    }

    record InspectBan(PlayerId playerId) implements IdentityAdministrationCommand {
        public InspectBan {
            playerId = Objects.requireNonNull(playerId, "playerId");
        }
    }

    record ReserveHandle(
            CanonicalHandle handle, PlayerId playerId, LocalHandleAdministrationReason reason)
            implements IdentityAdministrationCommand {
        public ReserveHandle {
            handle = Objects.requireNonNull(handle, "handle");
            playerId = Objects.requireNonNull(playerId, "playerId");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    record UnbindHandle(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            LocalHandleAdministrationReason reason)
            implements IdentityAdministrationCommand {
        public UnbindHandle {
            handle = Objects.requireNonNull(handle, "handle");
            expectedPlayerId = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    record RebindHandle(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId,
            LocalHandleAdministrationReason reason)
            implements IdentityAdministrationCommand {
        public RebindHandle {
            handle = Objects.requireNonNull(handle, "handle");
            expectedPlayerId = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
            replacementPlayerId =
                    Objects.requireNonNull(replacementPlayerId, "replacementPlayerId");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    record BanPlayer(PlayerId playerId, LocalHandleAdministrationReason reason)
            implements IdentityAdministrationCommand {
        public BanPlayer {
            playerId = Objects.requireNonNull(playerId, "playerId");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    record UnbanPlayer(PlayerId playerId, LocalHandleAdministrationReason reason)
            implements IdentityAdministrationCommand {
        public UnbanPlayer {
            playerId = Objects.requireNonNull(playerId, "playerId");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }
}
