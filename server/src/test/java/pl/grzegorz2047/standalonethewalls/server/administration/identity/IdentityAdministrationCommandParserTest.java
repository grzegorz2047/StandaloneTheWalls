package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class IdentityAdministrationCommandParserTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");

    @Test
    void parsesEverySupportedCommandIntoBoundedValues() {
        assertThat(parse("identity", "list", "handles"))
                .isEqualTo(new IdentityAdministrationCommand.ListHandles());
        assertThat(parse("identity", "list", "bans"))
                .isEqualTo(new IdentityAdministrationCommand.ListBans());
        assertThat(parse("identity", "inspect", "handle", HANDLE.value()))
                .isEqualTo(new IdentityAdministrationCommand.InspectHandle(HANDLE));
        assertThat(parse("identity", "inspect", "ban", FIRST.value()))
                .isEqualTo(new IdentityAdministrationCommand.InspectBan(FIRST));
        assertThat(
                        parse(
                                "identity",
                                "reserve",
                                HANDLE.value(),
                                FIRST.value(),
                                REASON.value()))
                .isEqualTo(
                        new IdentityAdministrationCommand.ReserveHandle(
                                HANDLE, FIRST, REASON));
        assertThat(
                        parse(
                                "identity",
                                "unbind",
                                HANDLE.value(),
                                FIRST.value(),
                                REASON.value()))
                .isEqualTo(
                        new IdentityAdministrationCommand.UnbindHandle(
                                HANDLE, FIRST, REASON));
        assertThat(
                        parse(
                                "identity",
                                "rebind",
                                HANDLE.value(),
                                FIRST.value(),
                                SECOND.value(),
                                REASON.value()))
                .isEqualTo(
                        new IdentityAdministrationCommand.RebindHandle(
                                HANDLE, FIRST, SECOND, REASON));
        assertThat(parse("identity", "ban-player-id", FIRST.value(), REASON.value()))
                .isEqualTo(new IdentityAdministrationCommand.BanPlayer(FIRST, REASON));
        assertThat(parse("identity", "unban-player-id", FIRST.value(), REASON.value()))
                .isEqualTo(new IdentityAdministrationCommand.UnbanPlayer(FIRST, REASON));
    }

    @Test
    void rejectsUnknownShapesAndInvalidBoundedArguments() {
        assertThatThrownBy(() -> parse())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("server", "list", "handles"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("identity", "unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("identity", "list", "handles", "extra"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("identity", "inspect", "unknown", FIRST.value()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                parse(
                                        "identity",
                                        "reserve",
                                        "NOT_CANONICAL",
                                        FIRST.value(),
                                        REASON.value()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                parse(
                                        "identity",
                                        "ban-player-id",
                                        "not-a-player-id",
                                        REASON.value()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                parse(
                                        "identity",
                                        "ban-player-id",
                                        FIRST.value(),
                                        " reason with edge space "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static IdentityAdministrationCommand parse(String... tokens) {
        return IdentityAdministrationCommandParser.parse(List.of(tokens));
    }
}
