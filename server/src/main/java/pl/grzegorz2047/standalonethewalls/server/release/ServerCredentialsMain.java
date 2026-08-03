package pl.grzegorz2047.standalonethewalls.server.release;

import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Strict command-line entry point for generating local dedicated-server credentials. */
public final class ServerCredentialsMain {
    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURE = 1;
    public static final int EXIT_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerCredentialsMain.class);

    private ServerCredentialsMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        int exitCode = run(arguments);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        try {
            Path output = parse(arguments);
            ServerCredentialGenerator.GeneratedCredentials generated =
                    ServerCredentialGenerator.generate(output);
            LOGGER.info(
                    "Dedicated-server credentials created; fingerprint={}",
                    generated.fingerprint().value());
            return EXIT_OK;
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Usage: sunderfront-server-credentials --output <directory>");
            return EXIT_USAGE;
        } catch (Exception exception) {
            LOGGER.error(
                    "Credential generation failed. Existing credential files were not overwritten.");
            return EXIT_FAILURE;
        }
    }

    private static Path parse(String[] arguments) {
        if (arguments.length != 2
                || !"--output".equals(Objects.requireNonNull(arguments[0], "argument"))) {
            throw new IllegalArgumentException("invalid credential generator arguments");
        }
        String value = Objects.requireNonNull(arguments[1], "output");
        if (value.isBlank() || value.startsWith("--")) {
            throw new IllegalArgumentException("invalid output directory");
        }
        return Path.of(value);
    }
}
