package pl.grzegorz2047.standalonethewalls.identity.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class LocalHandleAdministrationValuesTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("local_player");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Manual correction");

    @Test
    void administratorIdIsBoundedLowercaseAscii() {
        assertThatThrownBy(() -> new LocalIdentityAdministratorId("Console"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalIdentityAdministratorId("-console"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalIdentityAdministratorId("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reasonIsTrimmedNfcBoundedAndControlFree() {
        assertThatThrownBy(() -> new LocalHandleAdministrationReason(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalHandleAdministrationReason(" padded"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalHandleAdministrationReason("e\u0301"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalHandleAdministrationReason("bad\nreason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new LocalHandleAdministrationReason(
                                        "x"
                                                .repeat(
                                                        LocalHandleAdministrationReason
                                                                        .MAXIMUM_CODE_POINTS
                                                                + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditEventRejectsActionShapesThatCannotRepresentTheirMutation() {
        Instant now = Instant.parse("2026-08-02T08:00:00Z");
        assertThatThrownBy(
                        () ->
                                new LocalHandleAuditEvent(
                                        1L,
                                        now,
                                        ADMINISTRATOR,
                                        LocalHandleAuditAction.RESERVE,
                                        HANDLE,
                                        Optional.of(FIRST),
                                        Optional.of(SECOND),
                                        REASON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new LocalHandleAuditEvent(
                                        1L,
                                        now,
                                        ADMINISTRATOR,
                                        LocalHandleAuditAction.REBIND,
                                        HANDLE,
                                        Optional.of(FIRST),
                                        Optional.of(FIRST),
                                        REASON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new LocalHandleAuditEvent(
                                        0L,
                                        now,
                                        ADMINISTRATOR,
                                        LocalHandleAuditAction.UNBIND,
                                        HANDLE,
                                        Optional.of(FIRST),
                                        Optional.empty(),
                                        REASON))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
