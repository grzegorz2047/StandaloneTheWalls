package pl.grzegorz2047.standalonethewalls.client.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/** Deterministic localized text lookup with an explicit English fallback. */
public final class ClientMessages {
    private static final String BASE_NAME = "i18n.messages";
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final ClientLanguage language;
    private final ResourceBundle primary;
    private final ResourceBundle fallback;

    private ClientMessages(ClientLanguage language) {
        this.language = Objects.requireNonNull(language, "language");
        primary = ResourceBundle.getBundle(BASE_NAME, language.locale(), CONTROL);
        fallback = language == ClientLanguage.ENGLISH
                ? primary
                : ResourceBundle.getBundle(BASE_NAME, ClientLanguage.ENGLISH.locale(), CONTROL);
    }

    public static ClientMessages forLanguage(ClientLanguage language) {
        return new ClientMessages(language);
    }

    public ClientLanguage language() {
        return language;
    }

    public String text(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        String pattern;
        if (primary.containsKey(key)) {
            pattern = primary.getString(key);
        } else if (fallback.containsKey(key)) {
            pattern = fallback.getString(key);
        } else {
            throw new MissingResourceException(
                    "missing client localization key", BASE_NAME, key);
        }
        return new MessageFormat(pattern, language.locale()).format(arguments);
    }
}
