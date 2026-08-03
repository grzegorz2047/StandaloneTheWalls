package pl.grzegorz2047.standalonethewalls.client;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;

/** Strict first-screen client command-line options. */
public record ClientLaunchOptions(
        ClientLanguage language, boolean smokeMode, Path dataDirectory) {
    public ClientLaunchOptions {
        Objects.requireNonNull(language, "language");
        dataDirectory =
                Objects.requireNonNull(dataDirectory, "dataDirectory")
                        .toAbsolutePath()
                        .normalize();
    }

    public static ClientLaunchOptions parse(String[] arguments) {
        return parse(arguments, Locale.getDefault());
    }

    static ClientLaunchOptions parse(String[] arguments, Locale defaultLocale) {
        Objects.requireNonNull(arguments, "arguments");
        ClientLanguage language = ClientLanguage.fromLocale(defaultLocale);
        Path dataDirectory = Path.of("data");
        boolean languageSet = false;
        boolean dataDirectorySet = false;
        boolean smoke = false;
        for (int index = 0; index < arguments.length; index++) {
            String argument = requireArgument(arguments[index]);
            switch (argument) {
                case "--lang" -> {
                    if (languageSet) {
                        throw new IllegalArgumentException("--lang may be supplied only once");
                    }
                    String languageCode = requireValue(arguments, ++index, "--lang");
                    language = ClientLanguage.parse(languageCode);
                    languageSet = true;
                }
                case "--data-dir" -> {
                    if (dataDirectorySet) {
                        throw new IllegalArgumentException(
                                "--data-dir may be supplied only once");
                    }
                    dataDirectory = Path.of(requireValue(arguments, ++index, "--data-dir"));
                    dataDirectorySet = true;
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
        return new ClientLaunchOptions(language, smoke, dataDirectory);
    }

    private static String requireValue(String[] arguments, int index, String option) {
        if (index >= arguments.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        String value = requireArgument(arguments[index]);
        if (value.isBlank() || value.startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return value;
    }

    private static String requireArgument(String argument) {
        if (argument == null) {
            throw new IllegalArgumentException("arguments cannot contain null values");
        }
        return argument;
    }
}
