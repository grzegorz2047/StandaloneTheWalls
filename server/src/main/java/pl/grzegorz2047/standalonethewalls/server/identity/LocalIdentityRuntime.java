package pl.grzegorz2047.standalonethewalls.server.identity;

import java.time.Clock;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationService;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionService;
import pl.grzegorz2047.standalonethewalls.identity.policy.sqlite.SqliteLocalHandleAdministrationStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.sqlite.SqliteLocalPlayerBanAdministrationStore;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.AtomicRegistrySnapshotStore;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotAvailability;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotBundleFile;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommandService;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationResponse;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationResult;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationService;

/**
 * One local identity composition shared by session admission and administration. Missing or
 * rejected registry data remains an explicit runtime state rather than a generated default.
 */
public final class LocalIdentityRuntime {
    private final LocalIdentityRuntimeConfiguration configuration;
    private final Clock clock;
    private final RegistrySnapshotPolicy registryPolicy;
    private final AtomicRegistrySnapshotStore registryStore;
    private final SessionIdentityAdmissionService admission;
    private final IdentityAdministrationCommandService administration;
    private final RegistryAdministrationResult startupRegistryResult;

    private LocalIdentityRuntime(
            LocalIdentityRuntimeConfiguration configuration,
            Clock clock,
            RegistrySnapshotPolicy registryPolicy,
            AtomicRegistrySnapshotStore registryStore,
            SessionIdentityAdmissionService admission,
            IdentityAdministrationCommandService administration,
            RegistryAdministrationResult startupRegistryResult) {
        this.configuration = configuration;
        this.clock = clock;
        this.registryPolicy = registryPolicy;
        this.registryStore = registryStore;
        this.admission = admission;
        this.administration = administration;
        this.startupRegistryResult = startupRegistryResult;
    }

    public static LocalIdentityRuntime open(
            LocalIdentityRuntimeConfiguration configuration,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy registryPolicy,
            Clock clock) {
        LocalIdentityRuntimeConfiguration localConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        RegistryTrustBundle trustedRoots = Objects.requireNonNull(trustBundle, "trustBundle");
        RegistrySnapshotPolicy snapshotPolicy =
                Objects.requireNonNull(registryPolicy, "registryPolicy");
        Clock timeSource = Objects.requireNonNull(clock, "clock");

        SqliteLocalPlayerBanAdministrationStore banStore =
                new SqliteLocalPlayerBanAdministrationStore(
                        localConfiguration.sqliteDatabasePath());
        SqliteLocalHandleAdministrationStore handleStore =
                new SqliteLocalHandleAdministrationStore(localConfiguration.sqliteDatabasePath());

        AtomicRegistrySnapshotStore registryStore = new AtomicRegistrySnapshotStore();
        RegistrySnapshotService registrySnapshots =
                new RegistrySnapshotService(
                        new RegistrySnapshotVerifier(timeSource),
                        trustedRoots,
                        snapshotPolicy,
                        registryStore);
        RegistryAdministrationService registryAdministration =
                new RegistryAdministrationService(
                        registrySnapshots,
                        new RegistrySnapshotBundleFile(localConfiguration.registryBundlePath()));

        LocalHandleAdministrationService handleAdministration =
                new LocalHandleAdministrationService(handleStore, timeSource);
        LocalPlayerBanAdministrationService banAdministration =
                new LocalPlayerBanAdministrationService(banStore, timeSource);
        SessionIdentityAdmissionService admission =
                new SessionIdentityAdmissionService(
                        new PlayerBanAdmissionService(banStore),
                        new HandleAuthorizationService(handleStore));
        IdentityAdministrationCommandService administration =
                new IdentityAdministrationCommandService(
                        handleAdministration, banAdministration, registryAdministration);
        RegistryAdministrationResult startupRegistryResult =
                registryAdministration.reloadRegistry();

        return new LocalIdentityRuntime(
                localConfiguration,
                timeSource,
                snapshotPolicy,
                registryStore,
                admission,
                administration,
                startupRegistryResult);
    }

    public LocalIdentityRuntimeConfiguration configuration() {
        return configuration;
    }

    public RegistryAdministrationResult startupRegistryResult() {
        return startupRegistryResult;
    }

    public RegistrySnapshotAvailability registryAvailability() {
        return registryStore.availability(clock, registryPolicy);
    }

    public SessionIdentityAdmissionDecision admit(CanonicalHandle handle, PlayerId playerId) {
        return admission.evaluate(
                configuration.authorizationMode(),
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(playerId, "playerId"),
                registryAvailability());
    }

    public IdentityAdministrationResponse execute(
            IdentityAdministrationCommand command, IdentityAdministrationPrincipal principal) {
        return administration.execute(
                Objects.requireNonNull(command, "command"),
                Objects.requireNonNull(principal, "principal"));
    }
}
