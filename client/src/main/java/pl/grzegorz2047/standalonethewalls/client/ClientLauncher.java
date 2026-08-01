package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Process adapter for display and headless smoke modes. */
public final class ClientLauncher {
    public static final int EXIT_OK = 0;
    public static final int EXIT_STARTUP_FAILURE = 1;
    public static final int EXIT_USAGE = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLauncher.class);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(20);

    private ClientLauncher() {
        throw new AssertionError("No instances");
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        try {
            ClientLaunchOptions options = ClientLaunchOptions.parse(arguments);
            ClientMessages messages = ClientMessages.forLanguage(options.language());
            SunderfrontClient application = new SunderfrontClient(messages, options.smokeMode());
            configure(application);
            if (options.smokeMode()) {
                return runSmoke(application);
            }
            application.start();
            return EXIT_OK;
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Client command-line error: {}", exception.getMessage());
            return EXIT_USAGE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Client startup interrupted.");
            return EXIT_STARTUP_FAILURE;
        } catch (TimeoutException | RuntimeException exception) {
            LOGGER.error("Client startup failed.", exception);
            return EXIT_STARTUP_FAILURE;
        }
    }

    private static void configure(SunderfrontClient application) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(BuildInfo.PRODUCT_NAME);
        settings.setResolution(1280, 720);
        settings.setVSync(true);
        settings.setResizable(true);
        application.setSettings(settings);
        application.setShowSettings(false);
        application.setPauseOnLostFocus(false);
    }

    private static int runSmoke(SunderfrontClient application)
            throws InterruptedException, TimeoutException {
        try {
            application.start(JmeContext.Type.Headless, true);
            application.awaitInitialization(INITIALIZATION_TIMEOUT);
            return EXIT_OK;
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }
}
