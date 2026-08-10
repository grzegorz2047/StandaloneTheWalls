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
        assertFalse(defaults.preparationSmoke());
        assertEquals(ClientGraphicsQualityOption.UNCHANGED, defaults.graphicsQualityOption());
        assertEquals(Path.of("data").toAbsolutePath().normalize(), defaults.dataDirectory());
        assertEquals(ClientLanguage.ENGLISH, explicit.language());
        assertTrue(explicit.smokeMode());
        assertFalse(explicit.preparationSmoke());
        assertEquals(ClientGraphicsQualityOption.UNCHANGED, explicit.graphicsQualityOption());
        assertEquals(
                Path.of("runtime/client").toAbsolutePath().normalize(), explicit.dataDirectory());
    }

    @Test
    void acceptsGraphicsPresetAutoAndExplicitPresetsCaseInsensitively() {
        assertEquals(
                ClientGraphicsQualityOption.AUTO,
                ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset", "auto"}, Locale.ENGLISH, null)
                        .graphicsQualityOption());
        assertEquals(
                ClientGraphicsQualityOption.LOW,
                ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset", "LOW"}, Locale.ENGLISH, null)
                        .graphicsQualityOption());
        assertEquals(
                ClientGraphicsQualityOption.MEDIUM,
                ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset", "Medium"}, Locale.ENGLISH, null)
                        .graphicsQualityOption());
        assertEquals(
                ClientGraphicsQualityOption.HIGH,
                ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset", "high"}, Locale.ENGLISH, null)
                        .graphicsQualityOption());
    }

    @Test
    void preparationSmokeSelectsTheBoundedHeadlessScenario() {
        ClientLaunchOptions options =
                ClientLaunchOptions.parse(
                        new String[] {"--preparation-smoke"}, Locale.ENGLISH, null);

        assertTrue(options.smokeMode());
        assertTrue(options.preparationSmoke());
        assertEquals(ClientGraphicsQualityOption.UNCHANGED, options.graphicsQualityOption());
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
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--smoke", "--preparation-smoke"},
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--preparation-smoke", "--preparation-smoke"},
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ClientLaunchOptions(
                                ClientLanguage.ENGLISH,
                                false,
                                true,
                                ClientGraphicsQualityOption.UNCHANGED,
                                Path.of("data")));
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
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--graphics-preset", "ultra"}, Locale.ENGLISH, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {
                                    "--graphics-preset", "low", "--graphics-preset", "high"
                                },
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--smoke", "--graphics-preset", "low"},
                                Locale.ENGLISH,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientLaunchOptions.parse(
                                new String[] {"--preparation-smoke", "--graphics-preset", "auto"},
                                Locale.ENGLISH,
                                null));
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
