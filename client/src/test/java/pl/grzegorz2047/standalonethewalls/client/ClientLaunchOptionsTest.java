package pl.grzegorz2047.standalonethewalls.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;

class ClientLaunchOptionsTest {
    @Test
    void usesSystemLanguageAndAcceptsExplicitSmokeOverride() {
        ClientLaunchOptions defaults = ClientLaunchOptions.parse(new String[0], Locale.forLanguageTag("pl-PL"));
        ClientLaunchOptions explicit = ClientLaunchOptions.parse(
                new String[] {"--lang", "en", "--smoke"}, Locale.forLanguageTag("pl-PL"));

        assertEquals(ClientLanguage.POLISH, defaults.language());
        assertFalse(defaults.smokeMode());
        assertEquals(ClientLanguage.ENGLISH, explicit.language());
        assertTrue(explicit.smokeMode());
    }

    @Test
    void rejectsUnknownDuplicateIncompleteAndNullOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--lang"}, Locale.ENGLISH));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(
                        new String[] {"--lang", "en", "--lang", "pl"}, Locale.ENGLISH));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--smoke", "--smoke"}, Locale.ENGLISH));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--unknown"}, Locale.ENGLISH));
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientLaunchOptions.parse(new String[] {"--lang", null}, Locale.ENGLISH));
    }
}
