package pl.grzegorz2047.standalonethewalls.client;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;

/** Strict first-screen client command-line options. */
public record ClientLaunchOptions(
        ClientLanguage language,
        boolean smokeMode,
        boolean preparationSmoke,
        ClientGraphicsQualityOption graphicsQualityOption,
        Path dataDirectory) {
    private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";

    public ClientLaunchOptions {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(graphicsQualityOption, "graphicsQualityOption");
        if (preparationSmoke && !smokeMode) {
            throw new IllegalArgumentException("preparation smoke requires smoke mode");
        }
        if (smokeMode && graphicsQualityOption.changesPersistedState()) {
            throw new IllegalArgumentException(
                    "--graphics-preset cannot be combined with smoke mode");
        }
        dataDirectory =
                Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }

    public static ClientLaunchOptions parse(String[] arguments) {
        return parse(arguments, Locale.getDefault());
    }

    static ClientLaunchOptions parse(String[] arguments, Locale defaultLocale) {
        return parse(arguments, defaultLocale, System.getProperty(JPACKAGE_APP_PATH_PROPERTY));
    }

    static ClientLaunchOptions parse(
            String[] arguments, Locale defaultLocale, String packagedLauncherPath) {
        Objects.requireNonNull(arguments, "arguments");
        ClientLanguage language = ClientLanguage.fromLocale(defaultLocale);
        ClientGraphicsQualityOption graphicsQualityOption = ClientGraphicsQualityOption.UNCHANGED;
        Path dataDirectory = null;
        boolean languageSet = false;
        boolean dataDirectorySet = false;
        boolean graphicsQualitySet = false;
        boolean smoke = false;
        boolean preparationSmoke = false;
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
                        throw new IllegalArgumentException("--data-dir may be supplied only once");
                    }
                    dataDirectory = Path.of(requireValue(arguments, ++index, "--data-dir"));
                    dataDirectorySet = true;
                }
                case "--graphics-preset" -> {
                    if (graphicsQualitySet) {
                        throw new IllegalArgumentException(
                                "--graphics-preset may be supplied only once");
                    }
                    graphicsQualityOption =
                            ClientGraphicsQualityOption.parse(
                                    requireValue(arguments, ++index, "--graphics-preset"));
                    graphicsQualitySet = true;
                }
                case "--smoke" -> {
                    if (smoke) {
                        throw new IllegalArgumentException("smoke mode may be supplied only once");
                    }
                    smoke = true;
                }
                case "--preparation-smoke" -> {
                    if (smoke) {
                        throw new IllegalArgumentException("smoke mode may be supplied only once");
                    }
                    smoke = true;
                    preparationSmoke = true;
                }
                default -> throw new IllegalArgumentException("unknown argument: " + argument);
            }
        }
        if (!dataDirectorySet) {
            dataDirectory = defaultDataDirectory(packagedLauncherPath);
        }
        return new ClientLaunchOptions(
                language, smoke, preparationSmoke, graphicsQualityOption, dataDirectory);
    }

    private static Path defaultDataDirectory(String packagedLauncherPath) {
        if (packagedLauncherPath == null || packagedLauncherPath.isBlank()) {
            return Path.of("data");
        }
        Path launcher = Path.of(packagedLauncherPath).toAbsolutePath().normalize();
        Path launcherDirectory = launcher.getParent();
        if (launcherDirectory == null) {
            throw new IllegalArgumentException("packaged launcher path must have a parent");
        }
        return launcherDirectory.resolve("data");
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
