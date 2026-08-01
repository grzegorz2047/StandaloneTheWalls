package pl.grzegorz2047.standalonethewalls.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLauncherTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesConfigurationWithoutStartingTheRuntime() throws Exception {
        Path configuration = temporaryDirectory.resolve("server.properties");
        Files.writeString(configuration, "server.name=Validation Arena\nserver.tick-rate=20\n");

        assertEquals(
                ServerLauncher.EXIT_OK,
                ServerLauncher.run(
                        new String[] {"--config", configuration.toString(), "--validate-config"}));
    }

    @Test
    void runsABoundedHeadlessSmokeAndRejectsBadArguments() {
        assertEquals(
                ServerLauncher.EXIT_OK, ServerLauncher.run(new String[] {"--run-for-ticks", "3"}));
        assertEquals(
                ServerLauncher.EXIT_USAGE_OR_CONFIGURATION,
                ServerLauncher.run(new String[] {"--run-for-ticks", "0"}));
        assertEquals(
                ServerLauncher.EXIT_USAGE_OR_CONFIGURATION,
                ServerLauncher.run(new String[] {"--unknown"}));
    }
}
