package pl.grzegorz2047.standalonethewalls.client.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClientLanguageTest {
    @Test
    void parsesSupportedCodesAndFallsBackToEnglishForOtherSystemLocales() {
        assertEquals(ClientLanguage.ENGLISH, ClientLanguage.parse("EN"));
        assertEquals(ClientLanguage.POLISH, ClientLanguage.parse("pl"));
        assertEquals(
                ClientLanguage.POLISH, ClientLanguage.fromLocale(Locale.forLanguageTag("pl-PL")));
        assertEquals(ClientLanguage.ENGLISH, ClientLanguage.fromLocale(Locale.GERMAN));
        assertThrows(IllegalArgumentException.class, () -> ClientLanguage.parse("de"));
    }
}
