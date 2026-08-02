package pl.grzegorz2047.standalonethewalls.server.identity;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
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
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProvider;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotService;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotBundleFile;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotCachingRefreshService;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsProvider;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.CachingRegistryAdministrationService;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommandService;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationResponse;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationOperations;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationResult;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.RegistryAdministrationService;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshConfiguration;

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
    private final Optional<CachingRegistryAdministrationService> automaticRegistryRefresh;

    private LocalIdentityRuntime(
            LocalIdentityRuntimeConfiguration configuration,
            Clock clock,
            RegistrySnapshotPolicy registryPolicy,
            AtomicRegistrySnapshotStore registryStore,
            SessionIdentityAdmissionService admission,
            IdentityAdministrationCommandService administration,
            RegistryAdministrationResult startupRegistryResult,
            Optional<CachingRegistryAdministrationService> automaticRegistryRefresh) {
        this.configuration = configuration;
        this.clock = clock;
        this.registryPolicy = registryPolicy;
        this.registryStore = registryStore;
        this.admission = admission;
        this.administration = administration;
        this.startupRegistryResult = startupRegistryResult;
        this.automaticRegistryRefresh = automaticRegistryRefresh;
    }

    public static LocalIdentityRuntime open(
            LocalIdentityRuntimeConfiguration configuration,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy registryPolicy,
            Clock clock) {
        LocalIdentityRuntimeConfiguration runtimeConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        return open(
                runtimeConfiguration,
                trustBundle,
                registryPolicy,
                runtimeConfiguration.registryRefreshConfiguration(),
                clock);
    }

    public static LocalIdentityRuntime open(
            LocalIdentityRuntimeConfiguration configuration,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy registryPolicy,
            RegistryRefreshConfiguration refreshConfiguration,
            Clock clock) {
        return open(
                configuration,
                trustBundle,
                registryPolicy,
                refreshConfiguration,
                clock,
                RegistrySnapshotHttpsProvider::new);
    }

    static LocalIdentityRuntime open(
            LocalIdentityRuntimeConfiguration configuration,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy registryPolicy,
            RegistryRefreshConfiguration refreshConfiguration,
            Clock clock,
            RegistryRefreshProviderFactory providerFactory) {
        LocalIdentityRuntimeConfiguration localConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        RegistryTrustBundle trustedRoots = Objects.requireNonNull(trustBundle, "trustBundle");
        RegistrySnapshotPolicy snapshotPolicy =
                Objects.requireNonNull(registryPolicy, "registryPolicy");
        RegistryRefreshConfiguration refreshSource =
                Objects.requireNonNull(refreshConfiguration, "refreshConfiguration");
        Clock timeSource = Objects.requireNonNull(clock, "clock");
        RegistryRefreshProviderFactory refreshProviders =
                Objects.requireNonNull(providerFactory, "providerFactory");

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
        RegistrySnapshotBundleFile bundleFile =
                new RegistrySnapshotBundleFile(localConfiguration.registryBundlePath());
        RegistryAdministrationService localRegistryAdministration =
                new RegistryAdministrationService(registrySnapshots, bundleFile);
        RegistryAdministrationResult startupRegistryResult =
                localRegistryAdministration.reloadRegistry();
        RegistryAdministrationComposition registryAdministration =
                registryAdministration(
                        refreshSource,
                        refreshProviders,
                        registrySnapshots,
                        registryStore,
                        bundleFile,
                        localRegistryAdministration);

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
                        handleAdministration,
                        banAdministration,
                        registryAdministration.operations());

        return new LocalIdentityRuntime(
                localConfiguration,
                timeSource,
                snapshotPolicy,
                registryStore,
                admission,
                administration,
                startupRegistryResult,
                registryAdministration.automaticRefresh());
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

    public RegistryRefreshScheduler startAutomaticRegistryRefresh() {
        if (!(configuration.registryRefreshConfiguration()
                instanceof RegistryRefreshConfiguration.Https https)) {
            return RegistryRefreshScheduler.disabled();
        }
        CachingRegistryAdministrationService refresh =
                automaticRegistryRefresh.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "HTTPS registry refresh is not configured"));
        return RegistryRefreshScheduler.start(https.schedule(), refresh::refreshAutomatically);
    }

    RegistryRefreshScheduler startAutomaticRegistryRefresh(
            Supplier<RegistryRefreshScheduler.TaskScheduler> taskSchedulerFactory,
            RegistryRefreshScheduler.JitterSource jitterSource) {
        if (!(configuration.registryRefreshConfiguration()
                instanceof RegistryRefreshConfiguration.Https https)) {
            return RegistryRefreshScheduler.disabled();
        }
        CachingRegistryAdministrationService refresh =
                automaticRegistryRefresh.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "HTTPS registry refresh is not configured"));
        return RegistryRefreshScheduler.start(
                https.schedule(),
                refresh::refreshAutomatically,
                taskSchedulerFactory,
                jitterSource);
    }

    private static RegistryAdministrationComposition registryAdministration(
            RegistryRefreshConfiguration refreshConfiguration,
            RegistryRefreshProviderFactory providerFactory,
            RegistrySnapshotService snapshots,
            AtomicRegistrySnapshotStore store,
            RegistrySnapshotBundleFile bundleFile,
            RegistryAdministrationService localAdministration) {
        if (refreshConfiguration instanceof RegistryRefreshConfiguration.LocalBundle) {
            return new RegistryAdministrationComposition(localAdministration, Optional.empty());
        }
        RegistryRefreshConfiguration.Https https =
                (RegistryRefreshConfiguration.Https) refreshConfiguration;
        RegistrySnapshotProvider provider = providerFactory.create(https.configuration());
        CachingRegistryAdministrationService remoteAdministration =
                new CachingRegistryAdministrationService(
                        snapshots,
                        Objects.requireNonNull(provider, "HTTPS registry provider"),
                        new RegistrySnapshotCachingRefreshService(snapshots, bundleFile),
                        store);
        return new RegistryAdministrationComposition(
                remoteAdministration, Optional.of(remoteAdministration));
    }

    private record RegistryAdministrationComposition(
            RegistryAdministrationOperations operations,
            Optional<CachingRegistryAdministrationService> automaticRefresh) {
        private RegistryAdministrationComposition {
            operations = Objects.requireNonNull(operations, "operations");
            automaticRefresh = Objects.requireNonNull(automaticRefresh, "automaticRefresh");
        }
    }
}
