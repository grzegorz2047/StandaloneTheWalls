package pl.grzegorz2047.standalonethewalls.client;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;

class SunderfrontClientPointerSmokeTest {
    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void navigatesFromMenuToDirectConnectAndBackOnlyThroughPointerEvents() {
        SunderfrontClient client =
                new SunderfrontClient(
                        ClientMessages.forLanguage(ClientLanguage.ENGLISH),
                        true,
                        temporaryDirectory.resolve("pointer-client"));
        client.simpleInitApp();

        client.exercisePointerNavigation(Duration.ofSeconds(5));
    }
}
