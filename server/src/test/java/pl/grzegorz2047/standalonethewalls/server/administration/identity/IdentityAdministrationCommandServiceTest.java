package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalHandleBindingStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalPlayerBanStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAuditAction;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;

class IdentityAdministrationCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T11:00:00Z");
    private static final CanonicalHandle HANDLE = new CanonicalHandle("player_one");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");
    private static final RegistrySnapshotSummary SNAPSHOT =
            new RegistrySnapshotSummary(
                    7L,
                    NOW,
                    new RegistryRootId("sfr1_" + "a".repeat(52)),
                    "0".repeat(64),
                    1);

    @Test
    void permissionDenialHappensBeforeAnyMutationOrAudit() {
        Fixture fixture = fixture();
        IdentityAdministrationPrincipal principal = principal();

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.ReserveHandle(
                                                HANDLE, FIRST, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.MANAGE_HANDLE_BINDINGS));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.BanPlayer(FIRST, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.MANAGE_PLAYER_BANS));

        assertThat(fixture.handleStore().bindings()).isEmpty();
        assertThat(fixture.handleStore().auditEvents()).isEmpty();
        assertThat(fixture.banStore().bans()).isEmpty();
        assertThat(fixture.banStore().banAuditEvents()).isEmpty();
    }

    @Test
    void registryPermissionIsCheckedBeforeTheProviderBoundary() {
        Fixture fixture = fixture();

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.VerifySnapshot(),
                                        principal()))
                .isEqualTo(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.MANAGE_REGISTRY));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.ReloadRegistry(),
                                        principal()))
                .isEqualTo(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.MANAGE_REGISTRY));
        assertThat(fixture.registry().verifyCalls()).isZero();
        assertThat(fixture.registry().reloadCalls()).isZero();

        IdentityAdministrationPrincipal registryAdministrator =
                principal(IdentityAdministrationPermission.MANAGE_REGISTRY);
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.VerifySnapshot(),
                                        registryAdministrator))
                .isEqualTo(
                        new IdentityAdministrationResponse.RegistryOperation(
                                RegistryAdministrationResult.verified(SNAPSHOT)));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.ReloadRegistry(),
                                        registryAdministrator))
                .isEqualTo(
                        new IdentityAdministrationResponse.RegistryOperation(
                                RegistryAdministrationResult.activated(SNAPSHOT)));
        assertThat(fixture.registry().verifyCalls()).isOne();
        assertThat(fixture.registry().reloadCalls()).isOne();
    }

    @Test
    void handleCommandsDelegateToAtomicAuditedService() {
        Fixture fixture = fixture();
        IdentityAdministrationPrincipal principal =
                principal(
                        IdentityAdministrationPermission.VIEW_IDENTITY,
                        IdentityAdministrationPermission.MANAGE_HANDLE_BINDINGS);

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.ReserveHandle(
                                                HANDLE, FIRST, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.HandleMutation(
                                LocalHandleAdministrationResult.RESERVED));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.InspectHandle(HANDLE),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.HandleInspection(
                                HANDLE, Optional.of(FIRST)));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.ListHandles(), principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.Handles(
                                java.util.List.of(new LocalHandleBinding(HANDLE, FIRST))));

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.RebindHandle(
                                                HANDLE, FIRST, SECOND, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.HandleMutation(
                                LocalHandleAdministrationResult.REBOUND));
        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.UnbindHandle(
                                                HANDLE, SECOND, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.HandleMutation(
                                LocalHandleAdministrationResult.UNBOUND));

        assertThat(fixture.handleStore().bindings()).isEmpty();
        assertThat(fixture.handleStore().auditEvents())
                .extracting(event -> event.action())
                .containsExactly(
                        LocalHandleAuditAction.RESERVE,
                        LocalHandleAuditAction.REBIND,
                        LocalHandleAuditAction.UNBIND);
        assertThat(fixture.handleStore().auditEvents())
                .extracting(event -> event.administratorId())
                .containsOnly(ADMINISTRATOR);
    }

    @Test
    void banCommandsDelegateToAtomicAuditedServiceWithoutHandlePermission() {
        Fixture fixture = fixture();
        IdentityAdministrationPrincipal principal =
                principal(
                        IdentityAdministrationPermission.VIEW_IDENTITY,
                        IdentityAdministrationPermission.MANAGE_PLAYER_BANS);

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.BanPlayer(FIRST, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.BanMutation(
                                LocalPlayerBanAdministrationResult.BANNED));
        IdentityAdministrationResponse inspection =
                fixture.service()
                        .execute(new IdentityAdministrationCommand.InspectBan(FIRST), principal);
        assertThat(inspection).isInstanceOf(IdentityAdministrationResponse.BanInspection.class);
        assertThat(((IdentityAdministrationResponse.BanInspection) inspection).ban()).isPresent();
        assertThat(
                        fixture.service()
                                .execute(new IdentityAdministrationCommand.ListBans(), principal))
                .isInstanceOf(IdentityAdministrationResponse.Bans.class);

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.UnbanPlayer(
                                                FIRST, REASON),
                                        principal))
                .isEqualTo(
                        new IdentityAdministrationResponse.BanMutation(
                                LocalPlayerBanAdministrationResult.UNBANNED));
        assertThat(fixture.banStore().bans()).isEmpty();
        assertThat(fixture.banStore().banAuditEvents())
                .extracting(event -> event.action())
                .containsExactly(LocalPlayerBanAuditAction.BAN, LocalPlayerBanAuditAction.UNBAN);
        assertThat(fixture.handleStore().bindings()).isEmpty();
    }

    @Test
    void viewPermissionIsIndependentFromMutationPermissions() {
        Fixture fixture = fixture();
        IdentityAdministrationPrincipal handlesOnly =
                principal(IdentityAdministrationPermission.MANAGE_HANDLE_BINDINGS);

        assertThat(
                        fixture.service()
                                .execute(
                                        new IdentityAdministrationCommand.InspectHandle(HANDLE),
                                        handlesOnly))
                .isEqualTo(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.VIEW_IDENTITY));
    }

    private static Fixture fixture() {
        InMemoryLocalHandleBindingStore handleStore = new InMemoryLocalHandleBindingStore();
        InMemoryLocalPlayerBanStore banStore = new InMemoryLocalPlayerBanStore();
        CountingRegistryOperations registry = new CountingRegistryOperations();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new Fixture(
                handleStore,
                banStore,
                registry,
                new IdentityAdministrationCommandService(
                        new LocalHandleAdministrationService(handleStore, clock),
                        new LocalPlayerBanAdministrationService(banStore, clock),
                        registry));
    }

    private static IdentityAdministrationPrincipal principal(
            IdentityAdministrationPermission... permissions) {
        return new IdentityAdministrationPrincipal(ADMINISTRATOR, Set.of(permissions));
    }

    private static final class CountingRegistryOperations
            implements RegistryAdministrationOperations {
        private int verifyCalls;
        private int reloadCalls;

        @Override
        public RegistryAdministrationResult verifySnapshot() {
            verifyCalls++;
            return RegistryAdministrationResult.verified(SNAPSHOT);
        }

        @Override
        public RegistryAdministrationResult reloadRegistry() {
            reloadCalls++;
            return RegistryAdministrationResult.activated(SNAPSHOT);
        }

        int verifyCalls() {
            return verifyCalls;
        }

        int reloadCalls() {
            return reloadCalls;
        }
    }

    private record Fixture(
            InMemoryLocalHandleBindingStore handleStore,
            InMemoryLocalPlayerBanStore banStore,
            CountingRegistryOperations registry,
            IdentityAdministrationCommandService service) {}
}
