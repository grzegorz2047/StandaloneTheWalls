package pl.grzegorz2047.standalonethewalls.client.i18n;

import java.util.Locale;
import java.util.Objects;

/** Languages shipped by the first client screen. */
public enum ClientLanguage {
    ENGLISH("en", Locale.ENGLISH),
    POLISH("pl", Locale.forLanguageTag("pl"));

    private final String code;
    private final Locale locale;

    ClientLanguage(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    public static ClientLanguage parse(String code) {
        Objects.requireNonNull(code, "code");
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "en" -> ENGLISH;
            case "pl" -> POLISH;
            default -> throw new IllegalArgumentException("unsupported language: " + code);
        };
    }

    public static ClientLanguage fromLocale(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return "pl".equalsIgnoreCase(locale.getLanguage()) ? POLISH : ENGLISH;
    }
}
