package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.system.JmeContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;

class SunderfrontClientDirectConnectSmokeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @TempDir Path temporaryDirectory;

    @Test
    void opensDirectConnectAndReturnsToStartMenuInHeadlessContext()
            throws InterruptedException, TimeoutException {
        SunderfrontClient application =
                new SunderfrontClient(
                        ClientMessages.forLanguage(ClientLanguage.ENGLISH),
                        true,
                        temporaryDirectory.resolve("client-data"));
        try {
            application.start(JmeContext.Type.Headless, true);
            application.awaitInitialization(TIMEOUT);
            application.exerciseDirectConnectNavigation(TIMEOUT);
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }
}
