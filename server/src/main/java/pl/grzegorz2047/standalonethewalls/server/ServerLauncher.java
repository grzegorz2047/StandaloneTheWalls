package pl.grzegorz2047.standalonethewalls.server;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.identity.policy.sqlite.SqliteLocalHandleStoreException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.ServerConfigurationLoader;
import pl.grzegorz2047.standalonethewalls.server.config.identity.LocalIdentityProcessConfiguration;
import pl.grzegorz2047.standalonethewalls.server.config.identity.LocalIdentityProcessConfigurationLoader;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.server.runtime.FixedTickLoop;
import pl.grzegorz2047.standalonethewalls.server.runtime.ServerRuntime;
import pl.grzegorz2047.standalonethewalls.server.runtime.SystemNanoSleeper;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Command-line process adapter around the headless fixed-tick runtime. */
public final class ServerLauncher {
    public static final int EXIT_OK = 0;
    public static final int EXIT_RUNTIME_FAILURE = 1;
    public static final int EXIT_USAGE_OR_CONFIGURATION = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLauncher.class);
    private static final Duration SMOKE_TIMEOUT = Duration.ofSeconds(30);

    private ServerLauncher() {
        throw new AssertionError("No instances");
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
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
            if (options.validateOnly()) {
                if (identityConfiguration == null) {
                    LOGGER.info(
                            "Configuration valid for server '{}' ({} Hz, max {} players).",
                            configuration.name(),
                            configuration.tickRate(),
                            configuration.maximumPlayers());
                } else {
                    LOGGER.info(
                            "Configuration valid for server '{}' ({} Hz, max {} players) with local identity mode {}.",
                            configuration.name(),
                            configuration.tickRate(),
                            configuration.maximumPlayers(),
                            identityConfiguration.runtimeConfiguration().authorizationMode());
                }
                return EXIT_OK;
            }

            LocalIdentityRuntime identityRuntime = openIdentityRuntime(identityConfiguration);
            return runServer(configuration, options.runForTicks(), identityRuntime);
        } catch (IllegalArgumentException | IOException exception) {
            LOGGER.error("Server configuration or command-line error: {}", exception.getMessage());
            return EXIT_USAGE_OR_CONFIGURATION;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Server launcher interrupted.");
            return EXIT_RUNTIME_FAILURE;
        }
    }

    private static LocalIdentityProcessConfiguration loadIdentityConfiguration(Path path) {
        try {
            return LocalIdentityProcessConfigurationLoader.load(path);
        } catch (IOException | RegistrySnapshotException exception) {
            throw new IllegalArgumentException("local identity configuration is invalid", exception);
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
            LocalIdentityRuntime identityRuntime)
            throws InterruptedException {
        AtomicLong executedTicks = new AtomicLong();
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
                            long count = executedTicks.incrementAndGet();
                            if (runForTicks != null && count >= runForTicks) {
                                loop.requestStop();
                            }
                        });

        Thread shutdownHook =
                Thread.ofPlatform().name("sunderfront-shutdown").unstarted(runtime::close);
        boolean hookInstalled = false;
        try {
            if (runForTicks == null) {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                hookInstalled = true;
            }
            runtime.start();
            LOGGER.info(
                    "{} dedicated server '{}' started at {} Hz; reliable port {}, realtime port {}, max {} players; local identity {}.",
                    BuildInfo.PRODUCT_NAME,
                    configuration.name(),
                    configuration.tickRate(),
                    configuration.reliablePort(),
                    configuration.realtimePort(),
                    configuration.maximumPlayers(),
                    identityRuntime == null ? "disabled" : "enabled");

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
            LOGGER.info("Server stopped cleanly after {} ticks.", executedTicks.get());
            return EXIT_OK;
        } finally {
            runtime.close();
            if (hookInstalled) {
                removeShutdownHookIfPossible(shutdownHook);
            }
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
            boolean validateOnly,
            Long runForTicks) {
        private static LaunchOptions parse(String[] arguments) {
            Path configuration = null;
            Path identityConfiguration = null;
            boolean validate = false;
            Long ticks = null;
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
                    default -> throw new IllegalArgumentException("unknown argument: " + argument);
                }
            }
            if (validate && ticks != null) {
                throw new IllegalArgumentException(
                        "--validate-config cannot be combined with --run-for-ticks");
            }
            return new LaunchOptions(configuration, identityConfiguration, validate, ticks);
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return arguments[index];
        }
    }
}
