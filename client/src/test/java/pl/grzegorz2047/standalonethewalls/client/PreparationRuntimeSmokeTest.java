package pl.grzegorz2047.standalonethewalls.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparationRuntimeSmokeTest {
    @TempDir Path temporaryDirectory;

    @Test
    void runsThePreparationSmokeThroughTheProductionClientLauncher() {
        int exitCode =
                ClientLauncher.run(
                        new String[] {
                            "--preparation-smoke", "--data-dir", temporaryDirectory.toString()
                        });

        assertEquals(ClientLauncher.EXIT_OK, exitCode);
    }
}
