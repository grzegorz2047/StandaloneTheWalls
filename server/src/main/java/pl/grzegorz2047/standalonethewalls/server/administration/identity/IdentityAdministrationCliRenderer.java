package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBan;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;

/** Renders typed administration responses without relying on domain {@code toString()} output. */
public final class IdentityAdministrationCliRenderer {
    private IdentityAdministrationCliRenderer() {
        throw new AssertionError("No instances");
    }

    public static IdentityAdministrationCliOutput render(
            IdentityAdministrationResponse response) {
        IdentityAdministrationResponse result = Objects.requireNonNull(response, "response");
        List<String> lines = new ArrayList<>();
        lines.add("response=" + result.code().name());
        boolean successful =
                switch (result) {
                    case IdentityAdministrationResponse.PermissionDenied denied -> {
                        lines.add("requiredPermission=" + denied.requiredPermission().name());
                        yield false;
                    }
                    case IdentityAdministrationResponse.Handles handles -> {
                        lines.add("count=" + handles.bindings().size());
                        for (LocalHandleBinding binding : handles.bindings()) {
                            lines.add(
                                    "binding handle="
                                            + binding.handle().value()
                                            + " playerId="
                                            + binding.playerId().value());
                        }
                        yield true;
                    }
                    case IdentityAdministrationResponse.Bans bans -> {
                        lines.add("count=" + bans.bans().size());
                        for (LocalPlayerBan ban : bans.bans()) {
                            addBan(lines, "ban", ban);
                        }
                        yield true;
                    }
                    case IdentityAdministrationResponse.HandleInspection inspection -> {
                        lines.add("handle=" + inspection.handle().value());
                        lines.add("found=" + inspection.playerId().isPresent());
                        inspection.playerId()
                                .ifPresent(playerId -> lines.add("playerId=" + playerId.value()));
                        yield true;
                    }
                    case IdentityAdministrationResponse.BanInspection inspection -> {
                        lines.add("playerId=" + inspection.playerId().value());
                        lines.add("found=" + inspection.ban().isPresent());
                        inspection.ban().ifPresent(ban -> addBan(lines, "ban", ban));
                        yield true;
                    }
                    case IdentityAdministrationResponse.HandleMutation mutation -> {
                        lines.add("result=" + mutation.result().name());
                        yield handleMutationSucceeded(mutation.result());
                    }
                    case IdentityAdministrationResponse.BanMutation mutation -> {
                        lines.add("result=" + mutation.result().name());
                        yield banMutationSucceeded(mutation.result());
                    }
                    case IdentityAdministrationResponse.RegistryOperation operation ->
                            renderRegistry(lines, operation.result());
                };
        return new IdentityAdministrationCliOutput(lines, successful);
    }

    private static void addBan(List<String> lines, String prefix, LocalPlayerBan ban) {
        lines.add(
                prefix
                        + " playerId="
                        + ban.playerId().value()
                        + " bannedAt="
                        + ban.bannedAt()
                        + " administratorId="
                        + ban.administratorId().value()
                        + " reason="
                        + ban.reason().value());
    }

    private static boolean renderRegistry(
            List<String> lines, RegistryAdministrationResult result) {
        lines.add("result=" + result.code().name());
        result.snapshot()
                .ifPresent(
                        snapshot -> {
                            lines.add("sequence=" + snapshot.sequence());
                            lines.add("generatedAt=" + snapshot.generatedAt());
                            lines.add("rootKeyId=" + snapshot.rootKeyId().value());
                            lines.add("sha256=" + snapshot.sha256());
                            lines.add("entries=" + snapshot.entries());
                        });
        result.rejectionCode()
                .ifPresent(rejection -> lines.add("rejection=" + rejection.name()));
        return switch (result.code()) {
            case VERIFIED, ACTIVATED, UNCHANGED -> true;
            case PROVIDER_FAILURE, SNAPSHOT_REJECTED -> false;
        };
    }

    private static boolean handleMutationSucceeded(LocalHandleAdministrationResult result) {
        return switch (result) {
            case RESERVED, ALREADY_MATCHED, UNBOUND, REBOUND, SAME_PLAYER -> true;
            case CONFLICT, NOT_FOUND, EXPECTATION_MISMATCH, CAPACITY_EXCEEDED -> false;
        };
    }

    private static boolean banMutationSucceeded(LocalPlayerBanAdministrationResult result) {
        return switch (result) {
            case BANNED, ALREADY_BANNED, UNBANNED, NOT_BANNED -> true;
            case CAPACITY_EXCEEDED -> false;
        };
    }
}
