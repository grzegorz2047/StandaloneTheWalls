package pl.grzegorz2047.standalonethewalls.server;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchConfiguration;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.sqlite.SqliteLocalHandleStoreException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCliOutput;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCliRenderer;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommandParser;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPermission;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationResponse;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfigurationLoader;
import pl.grzegorz2047.standalonethewalls.server.config.identity.LocalIdentityProcessConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.identity.LocalIdentityProcessConfigurationLoader;
import pl.grzegorz2047.standalonethewalls.server.config.transport.ReliableTlsProcessConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.transport.ReliableTlsProcessConfigurationLoader;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.server.identity.RegistryRefreshScheduler;
import pl.grzegorz2047.standalonethewalls.server.identity.session.ReliableTlsAdmissionRuntime;
import pl.grzegorz2047.standalonethewalls.server.lobby.MinimalLobbyRuntime;
import pl.grzegorz2047.standalonethewalls.server.realtime.RealtimeTicketProvisioner;
import pl.grzegorz2047.standalonethewalls.server.runtime.FixedTickLoop;
import pl.grzegorz2047.standalonethewalls.server.runtime.ServerRuntime;
import pl.grzegorz2047.standalonethewalls.server.runtime.SystemNanoSleeper;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Command-line process adapter around the headless fixed-tick runtime. */
public final class ServerLauncher {
    public static final int EXIT_OK = 0;
    public static final int EXIT_RUNTIME_FAILURE = 1;
    public static final int EXIT_USAGE_OR_CONFIGURATION = 2;
    public static final int EXIT_ADMINISTRATION_REJECTED = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLauncher.class);
    private static final Duration SMOKE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LOBBY_SEND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LOBBY_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REALTIME_TICKET_LIFETIME = Duration.ofSeconds(30);
    private static final IdentityAdministrationPrincipal LOCAL_CLI_PRINCIPAL =
            new IdentityAdministrationPrincipal(
                    new LocalIdentityAdministratorId("local-cli"),
                    EnumSet.allOf(IdentityAdministrationPermission.class));

    private ServerLauncher() {
        throw new AssertionError("No instances");
    }

    public static int run(String[] arguments) {
        return run(arguments, System.out);
    }

    static int run(String[] arguments, PrintStream standardOutput) {
        Objects.requireNonNull(arguments, "arguments");
        PrintStream output = Objects.requireNonNull(standardOutput, "standardOutput");
        try {
            LaunchOptions options = LaunchOptions.parse(arguments);
            ServerConfiguration configuration =
                    options.configurationPath() == null
                            ? ServerConfiguration.defaults()
                            : ServerConfigurationLoader.load(options.configurationPath());
            LocalIdentityProcessConfiguration identityConfiguration =
                    options.identityConfigurationPath() == null
                            ? null
                            : loadIdentityConfiguration(options.identityConfigurationPath());
            ReliableTlsProcessConfiguration tlsConfiguration =
                    options.tlsConfigurationPath() == null
                            ? null
                            : ReliableTlsProcessConfigurationLoader.load(
                                    options.tlsConfigurationPath(), configuration);
            if (options.identityCommandTokens() != null) {
                return runIdentityCommand(
                        identityConfiguration, options.identityCommandTokens(), output);
            }
            if (options.validateOnly()) {
                logValidConfiguration(configuration, identityConfiguration, tlsConfiguration);
                return EXIT_OK;
            }

            LocalIdentityRuntime identityRuntime = openIdentityRuntime(identityConfiguration);
            return runServer(
                    configuration, options.runForTicks(), identityRuntime, tlsConfiguration);
        } catch (IllegalArgumentException | IOException exception) {
            LOGGER.error("Server configuration or command-line error: {}", exception.getMessage());
            return EXIT_USAGE_OR_CONFIGURATION;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Server launcher interrupted.");
            return EXIT_RUNTIME_FAILURE;
        }
    }

    private static void logValidConfiguration(
            ServerConfiguration server,
            LocalIdentityProcessConfiguration identity,
            ReliableTlsProcessConfiguration tls) {
        if (identity == null) {
            LOGGER.info(
                    "Configuration valid for server '{}' ({} Hz, max {} players); local identity and reliable TLS disabled.",
                    server.name(),
                    server.tickRate(),
                    server.maximumPlayers());
            return;
        }
        if (tls == null) {
            LOGGER.info(
                    "Configuration valid for server '{}' ({} Hz, max {} players) with local identity mode {}; reliable TLS disabled.",
                    server.name(),
                    server.tickRate(),
                    server.maximumPlayers(),
                    identity.runtimeConfiguration().authorizationMode());
            return;
        }
        LOGGER.info(
                "Configuration valid for server '{}' ({} Hz, max {} players) with local identity mode {} and reliable TLS server identity {}.",
                server.name(),
                server.tickRate(),
                server.maximumPlayers(),
                identity.runtimeConfiguration().authorizationMode(),
                tls.credentials().serverId().value());
    }

    private static int runIdentityCommand(
            LocalIdentityProcessConfiguration identityConfiguration,
            List<String> commandTokens,
            PrintStream output) {
        if (identityConfiguration == null) {
            throw new IllegalArgumentException("--identity-command requires --identity-config");
        }
        LocalIdentityRuntime runtime = openIdentityRuntime(identityConfiguration);
        IdentityAdministrationCommand command =
                IdentityAdministrationCommandParser.parse(commandTokens);
        IdentityAdministrationResponse response = runtime.execute(command, LOCAL_CLI_PRINCIPAL);
        IdentityAdministrationCliOutput rendered =
                IdentityAdministrationCliRenderer.render(response);
        rendered.lines().forEach(output::println);
        output.flush();
        return rendered.successful() ? EXIT_OK : EXIT_ADMINISTRATION_REJECTED;
    }

    private static LocalIdentityProcessConfiguration loadIdentityConfiguration(Path path) {
        try {
            return LocalIdentityProcessConfigurationLoader.load(path);
        } catch (IOException | RegistrySnapshotException exception) {
            throw new IllegalArgumentException(
                    "local identity configuration is invalid", exception);
        }
    }

    private static LocalIdentityRuntime openIdentityRuntime(
            LocalIdentityProcessConfiguration identityConfiguration) {
        if (identityConfiguration == null) {
            return null;
        }
        try {
            LocalIdentityRuntime runtime =
                    LocalIdentityRuntime.open(
                            identityConfiguration.runtimeConfiguration(),
                            identityConfiguration.trustBundle(),
                            identityConfiguration.registryPolicy(),
                            Clock.systemUTC());
            LOGGER.info(
                    "Local identity runtime opened in {} mode; registry startup {}, availability {}.",
                    runtime.configuration().authorizationMode(),
                    runtime.startupRegistryResult().code(),
                    runtime.registryAvailability().state());
            return runtime;
        } catch (SqliteLocalHandleStoreException exception) {
            throw new IllegalArgumentException(
                    "local identity persistence configuration is invalid", exception);
        }
    }

    private static int runServer(
            ServerConfiguration configuration,
            Long runForTicks,
            LocalIdentityRuntime identityRuntime,
            ReliableTlsProcessConfiguration tlsConfiguration)
            throws InterruptedException {
        AtomicLong executedTicks = new AtomicLong();
        AtomicReference<MinimalLobbyRuntime> lobbyTickTarget = new AtomicReference<>();
        FixedTickLoop loop =
                new FixedTickLoop(
                        configuration.tickRate(),
                        FixedTickLoop.DEFAULT_MAXIMUM_CATCH_UP_TICKS,
                        System::nanoTime,
                        new SystemNanoSleeper(),
                        skipped -> LOGGER.warn("Simulation skipped {} overdue ticks.", skipped));
        ServerRuntime runtime =
                new ServerRuntime(
                        loop,
                        tickNumber -> {
                            MinimalLobbyRuntime lobby = lobbyTickTarget.get();
                            if (lobby != null && !lobby.offerSimulationTick(tickNumber)) {
                                throw new IllegalStateException(
                                        "minimal lobby rejected an authoritative simulation tick");
                            }
                            long count = executedTicks.incrementAndGet();
                            if (runForTicks != null && count >= runForTicks) {
                                loop.requestStop();
                            }
                        });

        ReliableTlsAdmissionRuntime tlsRuntime = null;
        MinimalLobbyRuntime lobbyRuntime = null;
        RealtimeTicketProvisioner realtimeTicketProvisioner = null;
        RegistryRefreshScheduler registryRefreshScheduler = RegistryRefreshScheduler.disabled();
        Thread shutdownHook = null;
        boolean hookInstalled = false;
        try {
            if (tlsConfiguration != null) {
                tlsRuntime =
                        ReliableTlsAdmissionRuntime.open(
                                tlsConfiguration,
                                Objects.requireNonNull(
                                        identityRuntime,
                                        "reliable TLS requires local identity runtime"),
                                Clock.systemUTC(),
                                runtime::close);
                realtimeTicketProvisioner =
                        RealtimeTicketProvisioner.createProduction(
                                configuration.maximumPlayers(), REALTIME_TICKET_LIFETIME);
                lobbyRuntime =
                        new MinimalLobbyRuntime(
                                tlsRuntime.authorizedSessions(),
                                LobbyConfiguration.standard(),
                                MatchConfiguration.defaults(configuration.tickRate()),
                                realtimeTicketProvisioner,
                                LOBBY_SEND_TIMEOUT,
                                LOBBY_SHUTDOWN_TIMEOUT,
                                event ->
                                        LOGGER.debug(
                                                "Minimal lobby event {}; members {}, revision {}.",
                                                event.code(),
                                                event.memberCount(),
                                                event.revision()),
                                runtime::close);
                lobbyTickTarget.set(lobbyRuntime);
            }
            registryRefreshScheduler =
                    identityRuntime == null
                            ? RegistryRefreshScheduler.disabled()
                            : identityRuntime.startAutomaticRegistryRefresh();

            ReliableTlsAdmissionRuntime ownedTlsRuntime = tlsRuntime;
            MinimalLobbyRuntime ownedLobbyRuntime = lobbyRuntime;
            RealtimeTicketProvisioner ownedRealtimeTicketProvisioner = realtimeTicketProvisioner;
            RegistryRefreshScheduler ownedRegistryRefreshScheduler = registryRefreshScheduler;
            shutdownHook =
                    Thread.ofPlatform()
                            .name("sunderfront-shutdown")
                            .unstarted(
                                    () ->
                                            closeRuntime(
                                                    runtime,
                                                    ownedRegistryRefreshScheduler,
                                                    ownedTlsRuntime,
                                                    ownedLobbyRuntime,
                                                    ownedRealtimeTicketProvisioner));
            if (runForTicks == null) {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                hookInstalled = true;
            }
            if (lobbyRuntime != null) {
                lobbyRuntime.start();
            }
            if (tlsRuntime != null) {
                tlsRuntime.start();
            }
            runtime.start();
            LOGGER.info(
                    "{} dedicated server '{}' started at {} Hz; reliable port {}, configured realtime port {}, max {} players; local identity {}; reliable TLS {}; minimal lobby {}; realtime tickets {}.",
                    BuildInfo.PRODUCT_NAME,
                    configuration.name(),
                    configuration.tickRate(),
                    configuration.reliablePort(),
                    configuration.realtimePort(),
                    configuration.maximumPlayers(),
                    identityRuntime == null ? "disabled" : "enabled",
                    tlsRuntime == null ? "disabled" : "enabled",
                    lobbyRuntime == null ? "disabled" : "enabled",
                    realtimeTicketProvisioner == null
                            ? "disabled"
                            : realtimeTicketProvisioner.capability().reason());

            boolean terminated;
            if (runForTicks == null) {
                runtime.awaitTermination();
                terminated = true;
            } else {
                terminated = runtime.awaitTermination(SMOKE_TIMEOUT);
            }
            if (!terminated) {
                LOGGER.error("Server runtime did not finish within the smoke-test timeout.");
                return EXIT_RUNTIME_FAILURE;
            }
            if (runtime.failure().isPresent()) {
                LOGGER.error(
                        "Server runtime stopped after an internal failure.",
                        runtime.failure().orElseThrow());
                return EXIT_RUNTIME_FAILURE;
            }
            if (tlsRuntime != null && tlsRuntime.failure().isPresent()) {
                LOGGER.error("Reliable TLS listener stopped after an internal failure.");
                return EXIT_RUNTIME_FAILURE;
            }
            if (lobbyRuntime != null && lobbyRuntime.failure().isPresent()) {
                LOGGER.error("Minimal lobby stopped after an internal failure.");
                return EXIT_RUNTIME_FAILURE;
            }
            LOGGER.info("Server stopped cleanly after {} ticks.", executedTicks.get());
            return EXIT_OK;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Server process failed to start its reliable TLS/runtime resources.");
            return EXIT_RUNTIME_FAILURE;
        } finally {
            closeRuntime(
                    runtime,
                    registryRefreshScheduler,
                    tlsRuntime,
                    lobbyRuntime,
                    realtimeTicketProvisioner);
            lobbyTickTarget.set(null);
            if (hookInstalled && shutdownHook != null) {
                removeShutdownHookIfPossible(shutdownHook);
            }
        }
    }

    private static void closeRuntime(
            ServerRuntime runtime,
            RegistryRefreshScheduler registryRefreshScheduler,
            ReliableTlsAdmissionRuntime tlsRuntime,
            MinimalLobbyRuntime lobbyRuntime,
            RealtimeTicketProvisioner realtimeTicketProvisioner) {
        List<Throwable> failures = new ArrayList<>();
        if (tlsRuntime != null) {
            try {
                tlsRuntime.close();
            } catch (IOException | RuntimeException exception) {
                failures.add(exception);
            }
        }
        try {
            runtime.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        if (lobbyRuntime != null) {
            try {
                lobbyRuntime.close();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (realtimeTicketProvisioner != null) {
            try {
                realtimeTicketProvisioner.close();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        try {
            registryRefreshScheduler.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        if (!failures.isEmpty()) {
            LOGGER.error("Server shutdown completed with {} resource failure(s).", failures.size());
        }
    }

    private static void removeShutdownHookIfPossible(Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already in progress and owns the hook lifecycle.
        }
    }

    private record LaunchOptions(
            Path configurationPath,
            Path identityConfigurationPath,
            Path tlsConfigurationPath,
            boolean validateOnly,
            Long runForTicks,
            List<String> identityCommandTokens) {
        private static LaunchOptions parse(String[] arguments) {
            Path configuration = null;
            Path identityConfiguration = null;
            Path tlsConfiguration = null;
            boolean validate = false;
            Long ticks = null;
            List<String> commandTokens = null;
            for (int index = 0; index < arguments.length; index++) {
                String argument = Objects.requireNonNull(arguments[index], "argument");
                switch (argument) {
                    case "--config" -> {
                        if (configuration != null) {
                            throw new IllegalArgumentException(
                                    "--config may be supplied only once");
                        }
                        configuration = Path.of(requireValue(arguments, ++index, "--config"));
                    }
                    case "--identity-config" -> {
                        if (identityConfiguration != null) {
                            throw new IllegalArgumentException(
                                    "--identity-config may be supplied only once");
                        }
                        identityConfiguration =
                                Path.of(requireValue(arguments, ++index, "--identity-config"));
                    }
                    case "--tls-config" -> {
                        if (tlsConfiguration != null) {
                            throw new IllegalArgumentException(
                                    "--tls-config may be supplied only once");
                        }
                        tlsConfiguration =
                                Path.of(requireValue(arguments, ++index, "--tls-config"));
                    }
                    case "--validate-config" -> {
                        if (validate) {
                            throw new IllegalArgumentException(
                                    "--validate-config may be supplied only once");
                        }
                        validate = true;
                    }
                    case "--run-for-ticks" -> {
                        if (ticks != null) {
                            throw new IllegalArgumentException(
                                    "--run-for-ticks may be supplied only once");
                        }
                        String raw = requireValue(arguments, ++index, "--run-for-ticks");
                        try {
                            ticks = Long.parseLong(raw);
                        } catch (NumberFormatException exception) {
                            throw new IllegalArgumentException(
                                    "--run-for-ticks must be a base-10 integer", exception);
                        }
                        if (ticks < 1L || ticks > 1_000_000L) {
                            throw new IllegalArgumentException(
                                    "--run-for-ticks must be between 1 and 1000000");
                        }
                    }
                    case "--identity-command" -> {
                        int commandStart = index + 1;
                        if (commandStart >= arguments.length) {
                            throw new IllegalArgumentException(
                                    "--identity-command requires command tokens");
                        }
                        commandTokens =
                                List.copyOf(
                                        Arrays.asList(
                                                Arrays.copyOfRange(
                                                        arguments,
                                                        commandStart,
                                                        arguments.length)));
                        index = arguments.length;
                    }
                    default -> throw new IllegalArgumentException("unknown argument: " + argument);
                }
            }
            if (validate && ticks != null) {
                throw new IllegalArgumentException(
                        "--validate-config cannot be combined with --run-for-ticks");
            }
            if (tlsConfiguration != null && identityConfiguration == null) {
                throw new IllegalArgumentException("--tls-config requires --identity-config");
            }
            if (commandTokens != null) {
                if (identityConfiguration == null) {
                    throw new IllegalArgumentException(
                            "--identity-command requires --identity-config");
                }
                if (tlsConfiguration != null) {
                    throw new IllegalArgumentException(
                            "--identity-command cannot be combined with --tls-config");
                }
                if (validate) {
                    throw new IllegalArgumentException(
                            "--identity-command cannot be combined with --validate-config");
                }
                if (ticks != null) {
                    throw new IllegalArgumentException(
                            "--identity-command cannot be combined with --run-for-ticks");
                }
            }
            return new LaunchOptions(
                    configuration,
                    identityConfiguration,
                    tlsConfiguration,
                    validate,
                    ticks,
                    commandTokens);
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return arguments[index];
        }
    }
}
