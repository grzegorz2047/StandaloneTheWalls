package pl.grzegorz2047.standalonethewalls.client;

import java.util.Locale;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;

/** Strict first-screen client command-line options. */
public record ClientLaunchOptions(ClientLanguage language, boolean smokeMode) {
    public ClientLaunchOptions {
        Objects.requireNonNull(language, "language");
    }

    public static ClientLaunchOptions parse(String[] arguments) {
        return parse(arguments, Locale.getDefault());
    }

    static ClientLaunchOptions parse(String[] arguments, Locale defaultLocale) {
        Objects.requireNonNull(arguments, "arguments");
        ClientLanguage language = ClientLanguage.fromLocale(defaultLocale);
        boolean languageSet = false;
        boolean smoke = false;
        for (int index = 0; index < arguments.length; index++) {
            String argument = requireArgument(arguments[index]);
            switch (argument) {
                case "--lang" -> {
                    if (languageSet) {
                        throw new IllegalArgumentException("--lang may be supplied only once");
                    }
                    if (++index >= arguments.length) {
                        throw new IllegalArgumentException("--lang requires en or pl");
                    }
                    String languageCode = requireArgument(arguments[index]);
                    if (languageCode.startsWith("--")) {
                        throw new IllegalArgumentException("--lang requires en or pl");
                    }
                    language = ClientLanguage.parse(languageCode);
                    languageSet = true;
                }
                case "--smoke" -> {
                    if (smoke) {
                        throw new IllegalArgumentException("--smoke may be supplied only once");
                    }
                    smoke = true;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + argument);
            }
        }
        return new ClientLaunchOptions(language, smoke);
    }

    private static String requireArgument(String argument) {
        if (argument == null) {
            throw new IllegalArgumentException("arguments cannot contain null values");
        }
        return argument;
    }
}
