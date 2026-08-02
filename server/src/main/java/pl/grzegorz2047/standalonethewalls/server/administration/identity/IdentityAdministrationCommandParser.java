package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Strict parser for already-tokenized local identity administration commands. */
public final class IdentityAdministrationCommandParser {
    private IdentityAdministrationCommandParser() {
        throw new AssertionError("No instances");
    }

    public static IdentityAdministrationCommand parse(List<String> rawTokens) {
        List<String> tokens = List.copyOf(Objects.requireNonNull(rawTokens, "rawTokens"));
        if (tokens.size() < 2 || !"identity".equals(tokens.getFirst())) {
            throw new IllegalArgumentException("identity command must start with 'identity'");
        }
        return switch (tokens.get(1)) {
            case "list" -> parseList(tokens);
            case "inspect" -> parseInspect(tokens);
            case "reserve" -> parseReserve(tokens);
            case "unbind" -> parseUnbind(tokens);
            case "rebind" -> parseRebind(tokens);
            case "ban-player-id" -> parseBan(tokens);
            case "unban-player-id" -> parseUnban(tokens);
            default ->
                    throw new IllegalArgumentException(
                            "unknown identity command: " + tokens.get(1));
        };
    }

    private static IdentityAdministrationCommand parseList(List<String> tokens) {
        requireSize(tokens, 3);
        return switch (tokens.get(2)) {
            case "handles" -> new IdentityAdministrationCommand.ListHandles();
            case "bans" -> new IdentityAdministrationCommand.ListBans();
            default ->
                    throw new IllegalArgumentException(
                            "identity list target must be 'handles' or 'bans'");
        };
    }

    private static IdentityAdministrationCommand parseInspect(List<String> tokens) {
        requireSize(tokens, 4);
        return switch (tokens.get(2)) {
            case "handle" ->
                    new IdentityAdministrationCommand.InspectHandle(
                            new CanonicalHandle(tokens.get(3)));
            case "ban" -> new IdentityAdministrationCommand.InspectBan(new PlayerId(tokens.get(3)));
            default ->
                    throw new IllegalArgumentException(
                            "identity inspect target must be 'handle' or 'ban'");
        };
    }

    private static IdentityAdministrationCommand parseReserve(List<String> tokens) {
        requireSize(tokens, 5);
        return new IdentityAdministrationCommand.ReserveHandle(
                new CanonicalHandle(tokens.get(2)),
                new PlayerId(tokens.get(3)),
                new LocalHandleAdministrationReason(tokens.get(4)));
    }

    private static IdentityAdministrationCommand parseUnbind(List<String> tokens) {
        requireSize(tokens, 5);
        return new IdentityAdministrationCommand.UnbindHandle(
                new CanonicalHandle(tokens.get(2)),
                new PlayerId(tokens.get(3)),
                new LocalHandleAdministrationReason(tokens.get(4)));
    }

    private static IdentityAdministrationCommand parseRebind(List<String> tokens) {
        requireSize(tokens, 6);
        return new IdentityAdministrationCommand.RebindHandle(
                new CanonicalHandle(tokens.get(2)),
                new PlayerId(tokens.get(3)),
                new PlayerId(tokens.get(4)),
                new LocalHandleAdministrationReason(tokens.get(5)));
    }

    private static IdentityAdministrationCommand parseBan(List<String> tokens) {
        requireSize(tokens, 4);
        return new IdentityAdministrationCommand.BanPlayer(
                new PlayerId(tokens.get(2)), new LocalHandleAdministrationReason(tokens.get(3)));
    }

    private static IdentityAdministrationCommand parseUnban(List<String> tokens) {
        requireSize(tokens, 4);
        return new IdentityAdministrationCommand.UnbanPlayer(
                new PlayerId(tokens.get(2)), new LocalHandleAdministrationReason(tokens.get(3)));
    }

    private static void requireSize(List<String> tokens, int expected) {
        if (tokens.size() != expected) {
            throw new IllegalArgumentException(
                    "identity command expected "
                            + expected
                            + " tokens but received "
                            + tokens.size());
        }
    }
}
