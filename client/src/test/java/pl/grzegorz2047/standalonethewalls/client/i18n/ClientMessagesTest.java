package pl.grzegorz2047.standalonethewalls.client.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.MissingResourceException;
import org.junit.jupiter.api.Test;

class ClientMessagesTest {
    private static final List<String> START_SCREEN_KEYS =
            List.of(
                    "app.title",
                    "app.subtitle",
                    "menu.play",
                    "menu.settings",
                    "menu.exit",
                    "menu.unavailable",
                    "menu.help");

    @Test
    void loadsEnglishAndPolishStartScreenText() {
        ClientMessages english = ClientMessages.forLanguage(ClientLanguage.ENGLISH);
        ClientMessages polish = ClientMessages.forLanguage(ClientLanguage.POLISH);

        assertEquals("Play", english.text("menu.play"));
        assertEquals("Exit", english.text("menu.exit"));
        assertEquals("Graj", polish.text("menu.play"));
        assertEquals("Koniec", polish.text("menu.exit"));
        assertEquals("Buduj. Walcz. Przetrwaj.", polish.text("app.subtitle"));
        assertEquals("Strzałki góra/dół i Enter. Esc: koniec.", polish.text("menu.help"));
    }

    @Test
    void providesNonBlankVisibleStartScreenCopyForEveryLanguage() {
        for (ClientLanguage language : ClientLanguage.values()) {
            ClientMessages messages = ClientMessages.forLanguage(language);
            for (String key : START_SCREEN_KEYS) {
                assertFalse(messages.text(key).isBlank());
            }
        }
    }

    @Test
    void failsLoudlyForUnknownKeysInsteadOfDisplayingTheKey() {
        ClientMessages messages = ClientMessages.forLanguage(ClientLanguage.ENGLISH);

        assertThrows(MissingResourceException.class, () -> messages.text("missing.key"));
    }
}
