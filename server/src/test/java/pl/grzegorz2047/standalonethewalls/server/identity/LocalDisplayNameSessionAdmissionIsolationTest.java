package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalDisplayNameStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalHandleBindingStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalPlayerBanStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayName;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameExpectation;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;

class LocalDisplayNameSessionAdmissionIsolationTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId PLAYER = new PlayerId("sf1_" + "a".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");
    private static final Instant NOW = Instant.parse("2026-08-02T17:30:00Z");

    @Test
    void displayNameCannotBypassBanBeforeHandleAdmission() {
        InMemoryLocalDisplayNameStore displayNames = new InMemoryLocalDisplayNameStore();
        assertThat(
                        displayNames.setDisplayName(
                                PLAYER,
                                LocalDisplayNameExpectation.absent(),
                                new LocalDisplayName("Administrator looking name"),
                                ADMINISTRATOR,
                                REASON,
                                NOW))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        InMemoryLocalPlayerBanStore bans = new InMemoryLocalPlayerBanStore();
        assertThat(bans.ban(PLAYER, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);
        InMemoryLocalHandleBindingStore bindings = new InMemoryLocalHandleBindingStore();
        SessionIdentityAdmissionService admission =
                new SessionIdentityAdmissionService(
                        new PlayerBanAdmissionService(bans),
                        new HandleAuthorizationService(bindings));

        assertThat(
                        admission.evaluate(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                HANDLE,
                                PLAYER,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(SessionIdentityAdmissionDecision.PLAYER_BANNED);
        assertThat(bindings.find(HANDLE)).isEmpty();
        assertThat(displayNames.find(PLAYER))
                .contains(new LocalDisplayName("Administrator looking name"));
    }
}
