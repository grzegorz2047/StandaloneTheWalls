package pl.grzegorz2047.standalonethewalls.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;

class ClientLaunchOptionsTest {
    @Test
    void usesWorkingDirectoryDataForJvmDistributionAndAcceptsExplicitOverrides() {
        ClientLaunchOptions defaults =
                ClientLaunchOptions.parse(new String[0], Locale.forLanguageTag("pl-PL"), null);
        ClientLaunchOptions explicit =
                ClientLaunchOptions.parse(
                        new String[] {"--lang", "en", "--smoke", "--data-dir", "runtime/client"},
                        Locale.forLanguageTag("pl-PL"),
                        Path.of("build", "packaged", "Sunderfront.exe").toString());

        assertEquals(ClientLanguage.POLISH, defaults.language());
        assertFalse(defaults.smokeMode());
        assertEquals(Path.of("data").toAbsolutePath().normalize(), defaults.dataDirectory());
        assertEquals(ClientLanguage.ENGLISH, explicit.language());
        assertTrue(explicit.smokeMode());
        assertEquals(
                Path.of("runtime/client").toAbsolutePath().normalize(), explicit.dataDirectory());
    }

    @Test
    void resolvesDefaultDataBesideJpackageLauncher() {
        Path launcher =
                Path.of("build", "portable", "Sunderfront", "Sunderfront.exe")
                        .toAbsolutePath()
                        .normalize();

        ClientLaunchOptions packaged =
                ClientLaunchOptions.parse(new String[0], Locale.ENGLISH, launcher.toString());

        assertEquals(launcher.getParent().resolve("data"), packaged.dataDirectory());
    }

    @Test
    void rejectsUnknownDuplicateIncompleteAndNullOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--lang"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--lang", "en", "--lang", "pl"},
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--smoke", "--smoke"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--data-dir"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--data-dir", "one", "--data-dir", "two"},
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--data-dir", "--smoke"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--unknown"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--lang", null}, Locale.ENGLISH, null));
    }
}
