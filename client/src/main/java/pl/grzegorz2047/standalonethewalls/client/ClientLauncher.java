package pl.grzegorz2047.standalonethewalls.client;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsAutomaticQualitySelection;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsManualQualityOverride;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsQualityPreset;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsRuntimeQualitySelection;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsRuntimeRenderScaleState;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsRuntimeTextureQualityState;
import pl.grzegorz2047.standalonethewalls.client.performance.GraphicsTelemetryCaptureState;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Process adapter for display and headless smoke modes. */
public final class ClientLauncher {
    public static final int EXIT_OK = 0;
    public static final int EXIT_STARTUP_FAILURE = 1;
    public static final int EXIT_USAGE = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLauncher.class);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration PREPARATION_SMOKE_TIMEOUT = Duration.ofSeconds(20);

    private ClientLauncher() {
        throw new AssertionError("No instances");
    }

    public static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        try {
            ClientLaunchOptions options = ClientLaunchOptions.parse(arguments);
            if (options.preparationSmoke()) {
                PreparationRuntimeSmoke application = new PreparationRuntimeSmoke();
                configure(application);
                return runPreparationSmoke(application);
            }
            ClientMessages messages = ClientMessages.forLanguage(options.language());
            if (options.smokeMode()) {
                SunderfrontClient application =
                        new SunderfrontClient(messages, true, options.dataDirectory());
                configure(application);
                return runSmoke(application);
            }

            Optional<GraphicsQualityPreset> runtimePreset = resolveAutomaticRuntimePreset(options);
            SunderfrontClient application =
                    new SunderfrontClient(messages, false, options.dataDirectory());
            configure(application);
            runtimePreset.ifPresent(
                    preset -> {
                        application
                                .getStateManager()
                                .attach(new GraphicsRuntimeRenderScaleState(preset));
                        application
                                .getStateManager()
                                .attach(new GraphicsRuntimeTextureQualityState(preset));
                    });
            application.getStateManager().attach(new GraphicsTelemetryCaptureState());
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

    static Optional<GraphicsQualityPreset> resolveRuntimePreset(
            ClientLaunchOptions options, Optional<Path> assetLock) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(assetLock, "assetLock");
        if (assetLock.isEmpty()) {
            return Optional.empty();
        }
        try {
            return GraphicsRuntimeQualitySelection.compatiblePersistedPreset(
                    options.dataDirectory(), assetLock.orElseThrow());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Persisted graphics quality could not be applied; using native render scale.",
                    exception);
            return Optional.empty();
        }
    }

    private static Optional<GraphicsQualityPreset> resolveAutomaticRuntimePreset(
            ClientLaunchOptions options) throws InterruptedException {
        Optional<Path> assetLock;
        try {
            assetLock = ClientInstallationAssets.resolveAssetLock(ClientLauncher.class);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Packaged graphics assets could not be resolved; using native render scale.",
                    exception);
            return Optional.empty();
        }
        if (assetLock.isEmpty()) {
            LOGGER.warn("Packaged graphics asset lock is unavailable; using native render scale.");
            return Optional.empty();
        }

        Path currentAssetLock = assetLock.orElseThrow();
        try {
            GraphicsQualityPreset selected =
                    GraphicsAutomaticQualitySelection.resolve(
                            options.dataDirectory(), currentAssetLock);
            if (!options.graphicsQualityOption().changesPersistedState()) {
                return Optional.of(selected);
            }
            return Optional.of(
                    GraphicsManualQualityOverride.apply(
                            options.dataDirectory(),
                            currentAssetLock,
                            options.graphicsQualityOption().manualOverride()));
        } catch (InterruptedException exception) {
            throw exception;
        } catch (IOException | ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.warn(
                    "Automatic graphics quality selection failed; using native render scale.",
                    exception);
            return Optional.empty();
        }
    }

    private static void configure(SimpleApplication application) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(BuildInfo.PRODUCT_NAME + " " + BuildInfo.VERSION);
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

    private static int runPreparationSmoke(PreparationRuntimeSmoke application)
            throws InterruptedException, TimeoutException {
        try {
            application.start(JmeContext.Type.Headless, true);
            application.awaitCompletion(PREPARATION_SMOKE_TIMEOUT);
            return EXIT_OK;
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }
}
