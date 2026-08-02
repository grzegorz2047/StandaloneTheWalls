package pl.grzegorz2047.standalonethewalls.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationDecision;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalHandleBindingStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalPlayerBanStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;

class SessionIdentityAdmissionServiceTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");
    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    void bannedPlayerIsRejectedBeforeLocalFirstUseCanClaimTheHandle() {
        InMemoryLocalHandleBindingStore bindings = new InMemoryLocalHandleBindingStore();
        InMemoryLocalPlayerBanStore bans = new InMemoryLocalPlayerBanStore();
        assertThat(bans.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);
        SessionIdentityAdmissionService service = service(bindings, bans);

        assertThat(
                        service.evaluate(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                HANDLE,
                                FIRST,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(SessionIdentityAdmissionDecision.PLAYER_BANNED);
        assertThat(bindings.find(HANDLE)).isEmpty();
    }

    @Test
    void unbannedLocalPlayersKeepTheExistingTofuSemantics() {
        InMemoryLocalHandleBindingStore bindings = new InMemoryLocalHandleBindingStore();
        SessionIdentityAdmissionService service =
                service(bindings, new InMemoryLocalPlayerBanStore());

        SessionIdentityAdmissionDecision firstUse =
                service.evaluate(
                        HandleAuthorizationMode.LOCAL_TOFU,
                        HANDLE,
                        FIRST,
                        RegistrySnapshotAvailability.absent());
        assertThat(firstUse)
                .isEqualTo(SessionIdentityAdmissionDecision.LOCAL_FIRST_USE_ACCEPTED);
        assertThat(firstUse.isAccepted()).isTrue();
        assertThat(firstUse.verificationLevel())
                .contains(HandleVerificationLevel.LOCAL_UNVERIFIED);

        assertThat(
                        service.evaluate(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                HANDLE,
                                FIRST,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(SessionIdentityAdmissionDecision.LOCAL_RETURNING_ACCEPTED);
        assertThat(
                        service.evaluate(
                                HandleAuthorizationMode.LOCAL_TOFU,
                                HANDLE,
                                SECOND,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(SessionIdentityAdmissionDecision.LOCAL_BINDING_CONFLICT);
    }

    @Test
    void globalOnlyRegistryFailureDoesNotFallBackToLocalBinding() {
        InMemoryLocalHandleBindingStore bindings = new InMemoryLocalHandleBindingStore();
        SessionIdentityAdmissionService service =
                service(bindings, new InMemoryLocalPlayerBanStore());

        assertThat(
                        service.evaluate(
                                HandleAuthorizationMode.GLOBAL_ONLY,
                                HANDLE,
                                FIRST,
                                RegistrySnapshotAvailability.absent()))
                .isEqualTo(SessionIdentityAdmissionDecision.REGISTRY_UNAVAILABLE);
        assertThat(bindings.find(HANDLE)).isEmpty();
    }

    @Test
    void everyHandleDecisionKeepsItsCodeAcceptanceAndVerificationLevel() {
        for (HandleAuthorizationDecision handleDecision : HandleAuthorizationDecision.values()) {
            SessionIdentityAdmissionDecision sessionDecision =
                    SessionIdentityAdmissionDecision.fromHandle(handleDecision);

            assertThat(sessionDecision.name()).isEqualTo(handleDecision.name());
            assertThat(sessionDecision.isAccepted()).isEqualTo(handleDecision.isAccepted());
            assertThat(sessionDecision.verificationLevel())
                    .isEqualTo(handleDecision.verificationLevel());
        }
        assertThat(SessionIdentityAdmissionDecision.PLAYER_BANNED.isAccepted()).isFalse();
        assertThat(SessionIdentityAdmissionDecision.PLAYER_BANNED.verificationLevel()).isEmpty();
    }

    private static SessionIdentityAdmissionService service(
            InMemoryLocalHandleBindingStore bindings, InMemoryLocalPlayerBanStore bans) {
        return new SessionIdentityAdmissionService(
                new PlayerBanAdmissionService(bans), new HandleAuthorizationService(bindings));
    }
}
