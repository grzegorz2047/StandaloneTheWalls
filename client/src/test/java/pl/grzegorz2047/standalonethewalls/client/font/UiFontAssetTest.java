package pl.grzegorz2047.standalonethewalls.client.font;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiFontAssetTest {
    private static final String DEFAULT_FONT = "Interface/Fonts/Default.fnt";
    private static final String NAMED_FONT =
            "Interface/Fonts/SunderfrontUI-Regular.fnt";
    private static final String REQUIRED_POLISH = "ĄĆĘŁŃÓŚŹŻąćęłńóśźż";
    private static final String REQUIRED_SYMBOLS = "–—…„”’←→✓✗°×";

    @Test
    void loadsTheProjectOwnedUnicodeFontThroughTheProductionAssetPath() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);

        BitmapFont defaultFont = assetManager.loadFont(DEFAULT_FONT);
        BitmapFont namedFont = assetManager.loadFont(NAMED_FONT);

        assertThat(defaultFont.getCharSet().getRenderedSize()).isEqualTo(48);
        assertThat(defaultFont.getCharSet().getLineHeight()).isEqualTo(82);
        assertThat(defaultFont.getCharSet().getWidth()).isEqualTo(1024);
        assertThat(defaultFont.getCharSet().getHeight()).isEqualTo(256);
        assertThat(defaultFont.getPage(0)).isNotNull();
        assertThat(namedFont.getPage(0)).isNotNull();
        assertThat(namedFont.getCharSet().getRenderedSize())
                .isEqualTo(defaultFont.getCharSet().getRenderedSize());
    }

    @Test
    void coversEveryVisibleCharacterUsedByEnglishAndPolishLocalization() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);
        BitmapFont font = assetManager.loadFont(DEFAULT_FONT);
        Set<Integer> required = new LinkedHashSet<>();
        collectBundleCodePoints(required, Locale.ENGLISH);
        collectBundleCodePoints(required, Locale.forLanguageTag("pl-PL"));
        (REQUIRED_POLISH + REQUIRED_SYMBOLS)
                .codePoints()
                .forEach(required::add);

        Set<String> missing = new LinkedHashSet<>();
        for (int codePoint : required) {
            if (!Character.isWhitespace(codePoint)
                    && font.getCharSet().getCharacter(codePoint) == null) {
                missing.add(String.format("U+%04X", codePoint));
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void representativeLocalizedUiTextFitsTheSupported720pAnd1080pWidths() {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);
        BitmapFont font = assetManager.loadFont(DEFAULT_FONT);

        assertFits(font, "Połączenie bezpośrednie", 32.0f, 1200.0f);
        assertFits(
                font,
                "Strzałki góra/dół i Enter. Esc: koniec.",
                18.0f,
                1200.0f);
        assertFits(
                font,
                "Nie udało się załadować zweryfikowanej sceny przygotowania.",
                22.0f,
                1840.0f);
    }

    private static void collectBundleCodePoints(Set<Integer> target, Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
        for (String key : bundle.keySet()) {
            bundle.getString(key).codePoints().forEach(target::add);
        }
    }

    private static void assertFits(
            BitmapFont font, String value, float size, float maximumWidth) {
        BitmapText text = new BitmapText(font);
        text.setSize(size);
        text.setText(value);
        assertThat(text.getLineWidth()).isLessThanOrEqualTo(maximumWidth);
    }
}
