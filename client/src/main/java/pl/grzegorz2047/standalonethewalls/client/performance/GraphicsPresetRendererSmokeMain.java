package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** CI entrypoint that proves one graphics preset reaches completed renderer frames. */
public final class GraphicsPresetRendererSmokeMain {
    static final int EXIT_OK = 0;
    static final int EXIT_RENDERER_FAILURE = 1;
    static final int EXIT_USAGE = 2;
    static final String FORCE_SHADER_FALLBACK = "--force-shader-fallback";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final Duration PRESET_TIMEOUT = Duration.ofSeconds(20);
    private static final Logger LOGGER =
            LoggerFactory.getLogger(GraphicsPresetRendererSmokeMain.class);

    private GraphicsPresetRendererSmokeMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] arguments) {
        System.exit(run(arguments));
    }

    static int run(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length < 1 || arguments.length > 2) {
            LOGGER.error("Graphics renderer smoke requires a preset and optional fallback flag.");
            return EXIT_USAGE;
        }
        boolean forceShaderFallback = false;
        if (arguments.length == 2) {
            if (!FORCE_SHADER_FALLBACK.equals(arguments[1])) {
                LOGGER.error("Unknown graphics renderer smoke option: {}", arguments[1]);
                return EXIT_USAGE;
            }
            forceShaderFallback = true;
        }

        GraphicsQualityPreset preset;
        try {
            preset = GraphicsQualityPreset.valueOf(arguments[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Unknown graphics renderer smoke preset: {}", arguments[0]);
            return EXIT_USAGE;
        }
        return run(preset, forceShaderFallback);
    }

    static int run(GraphicsQualityPreset preset, boolean forceShaderFallback) {
        Objects.requireNonNull(preset, "preset");
        try {
            runPreset(preset, forceShaderFallback);
            return EXIT_OK;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Graphics preset renderer smoke was interrupted for {}.", preset);
            return EXIT_RENDERER_FAILURE;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.error("Graphics preset renderer smoke failed for {}.", preset, exception);
            return EXIT_RENDERER_FAILURE;
        }
    }

    static void validateSnapshot(
            GraphicsQualityPreset expectedPreset,
            boolean expectedFallbackUsed,
            GraphicsPresetRendererSmokeApplication.Snapshot snapshot) {
        if (snapshot.preset() != expectedPreset) {
            throw new IllegalStateException("renderer smoke completed for the wrong preset");
        }
        if (Double.compare(snapshot.renderScale(), expectedPreset.defaultRenderScale()) != 0) {
            throw new IllegalStateException(
                    "renderer smoke used an unexpected initial render scale");
        }
        boolean expectedOffscreen =
                GraphicsBenchmarkRenderScale.requiresOffscreenRendering(
                        expectedPreset.defaultRenderScale());
        if (snapshot.offscreenProcessorAttached() != expectedOffscreen) {
            throw new IllegalStateException("renderer smoke used the wrong framebuffer path");
        }
        if (snapshot.fallbackUsed() != expectedFallbackUsed) {
            throw new IllegalStateException("renderer smoke used the wrong material path");
        }
        if (snapshot.geometryCount()
                != GraphicsBenchmarkReferenceScene.geometryCount(expectedPreset)) {
            throw new IllegalStateException(
                    "renderer smoke scene topology does not match the preset");
        }
        if (snapshot.renderedFrames() < 1) {
            throw new IllegalStateException("renderer smoke did not complete a rendered frame");
        }
    }

    private static void runPreset(GraphicsQualityPreset preset, boolean forceShaderFallback)
            throws InterruptedException, ExecutionException, TimeoutException {
        LOGGER.info(
                "Starting graphics renderer smoke for {} with forcedFallback={}.",
                preset,
                forceShaderFallback);
        GraphicsPresetRendererSmokeApplication application =
                new GraphicsPresetRendererSmokeApplication(preset, forceShaderFallback);
        configure(application, preset);
        try {
            application.start(JmeContext.Type.Display, true);
            GraphicsPresetRendererSmokeApplication.Snapshot snapshot =
                    application.awaitCompletion(PRESET_TIMEOUT);
            validateSnapshot(preset, forceShaderFallback, snapshot);
            LOGGER.info(
                    "Graphics renderer smoke passed for {} at renderScale={} with {} geometries; fallbackUsed={}.",
                    preset,
                    snapshot.renderScale(),
                    snapshot.geometryCount(),
                    snapshot.fallbackUsed());
        } finally {
            if (application.getContext() != null) {
                application.stop(false);
            }
        }
    }

    private static void configure(
            GraphicsPresetRendererSmokeApplication application, GraphicsQualityPreset preset) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle(BuildInfo.PRODUCT_NAME + " " + preset + " Renderer Smoke");
        settings.setResolution(WIDTH, HEIGHT);
        settings.setFullscreen(false);
        settings.setVSync(false);
        settings.setResizable(false);
        settings.setAudioRenderer(null);
        application.setSettings(settings);
        application.setShowSettings(false);
        application.setPauseOnLostFocus(false);
    }
}
