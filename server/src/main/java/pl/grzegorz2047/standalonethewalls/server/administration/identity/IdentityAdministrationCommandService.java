package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationService;

/** Authorizes and executes typed local identity commands through atomic domain services. */
public final class IdentityAdministrationCommandService {
    private final LocalHandleAdministrationService handles;
    private final LocalPlayerBanAdministrationService bans;

    public IdentityAdministrationCommandService(
            LocalHandleAdministrationService handles,
            LocalPlayerBanAdministrationService bans) {
        this.handles = Objects.requireNonNull(handles, "handles");
        this.bans = Objects.requireNonNull(bans, "bans");
    }

    public IdentityAdministrationResponse execute(
            IdentityAdministrationCommand command, IdentityAdministrationPrincipal principal) {
        IdentityAdministrationCommand requestedCommand =
                Objects.requireNonNull(command, "command");
        IdentityAdministrationPrincipal authenticatedPrincipal =
                Objects.requireNonNull(principal, "principal");
        IdentityAdministrationPermission requiredPermission =
                requiredPermission(requestedCommand);
        if (!authenticatedPrincipal.has(requiredPermission)) {
            return new IdentityAdministrationResponse.PermissionDenied(requiredPermission);
        }

        return switch (requestedCommand) {
            case IdentityAdministrationCommand.ListHandles ignored ->
                    new IdentityAdministrationResponse.Handles(handles.bindings());
            case IdentityAdministrationCommand.ListBans ignored ->
                    new IdentityAdministrationResponse.Bans(bans.bans());
            case IdentityAdministrationCommand.InspectHandle inspect ->
                    new IdentityAdministrationResponse.HandleInspection(
                            inspect.handle(), handles.inspect(inspect.handle()));
            case IdentityAdministrationCommand.InspectBan inspect ->
                    new IdentityAdministrationResponse.BanInspection(
                            inspect.playerId(), bans.inspect(inspect.playerId()));
            case IdentityAdministrationCommand.ReserveHandle reserve ->
                    new IdentityAdministrationResponse.HandleMutation(
                            handles.reserve(
                                    reserve.handle(),
                                    reserve.playerId(),
                                    authenticatedPrincipal.administratorId(),
                                    reserve.reason()));
            case IdentityAdministrationCommand.UnbindHandle unbind ->
                    new IdentityAdministrationResponse.HandleMutation(
                            handles.unbind(
                                    unbind.handle(),
                                    unbind.expectedPlayerId(),
                                    authenticatedPrincipal.administratorId(),
                                    unbind.reason()));
            case IdentityAdministrationCommand.RebindHandle rebind ->
                    new IdentityAdministrationResponse.HandleMutation(
                            handles.rebind(
                                    rebind.handle(),
                                    rebind.expectedPlayerId(),
                                    rebind.replacementPlayerId(),
                                    authenticatedPrincipal.administratorId(),
                                    rebind.reason()));
            case IdentityAdministrationCommand.BanPlayer ban ->
                    new IdentityAdministrationResponse.BanMutation(
                            bans.ban(
                                    ban.playerId(),
                                    authenticatedPrincipal.administratorId(),
                                    ban.reason()));
            case IdentityAdministrationCommand.UnbanPlayer unban ->
                    new IdentityAdministrationResponse.BanMutation(
                            bans.unban(
                                    unban.playerId(),
                                    authenticatedPrincipal.administratorId(),
                                    unban.reason()));
        };
    }

    private static IdentityAdministrationPermission requiredPermission(
            IdentityAdministrationCommand command) {
        return switch (command) {
            case IdentityAdministrationCommand.ListHandles ignored,
                    IdentityAdministrationCommand.ListBans ignored,
                    IdentityAdministrationCommand.InspectHandle ignored,
                    IdentityAdministrationCommand.InspectBan ignored ->
                    IdentityAdministrationPermission.VIEW_IDENTITY;
            case IdentityAdministrationCommand.ReserveHandle ignored,
                    IdentityAdministrationCommand.UnbindHandle ignored,
                    IdentityAdministrationCommand.RebindHandle ignored ->
                    IdentityAdministrationPermission.MANAGE_HANDLE_BINDINGS;
            case IdentityAdministrationCommand.BanPlayer ignored,
                    IdentityAdministrationCommand.UnbanPlayer ignored ->
                    IdentityAdministrationPermission.MANAGE_PLAYER_BANS;
        };
    }
}
