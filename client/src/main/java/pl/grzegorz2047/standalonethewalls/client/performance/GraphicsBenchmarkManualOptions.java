package pl.grzegorz2047.standalonethewalls.client.performance;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Bounded explicit command-line inputs for a standalone manual graphics benchmark. */
record GraphicsBenchmarkManualOptions(
        GraphicsQualityPreset preset,
        int width,
        int height,
        double renderScale,
        int warmUpFrames,
        int measurementFrames,
        Path assetLock,
        Path outputDirectory) {
    static final int MINIMUM_WIDTH = 320;
    static final int MAXIMUM_WIDTH = 7_680;
    static final int MINIMUM_HEIGHT = 240;
    static final int MAXIMUM_HEIGHT = 4_320;

    GraphicsBenchmarkManualOptions {
        preset = Objects.requireNonNull(preset, "preset");
        if (width < MINIMUM_WIDTH || width > MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("width is outside the bounded range");
        }
        if (height < MINIMUM_HEIGHT || height > MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("height is outside the bounded range");
        }
        GraphicsBenchmarkRenderScale.requireScale(renderScale);
        if (renderScale < preset.minimumRenderScale()
                || renderScale > preset.maximumRenderScale()) {
            throw new IllegalArgumentException(
                    "render scale is outside the selected preset bounds");
        }
        if (warmUpFrames < 0 || warmUpFrames > GraphicsBenchmarkSession.MAXIMUM_WARM_UP_FRAMES) {
            throw new IllegalArgumentException("warm-up frame count is outside the bounded range");
        }
        if (measurementFrames < 1 || measurementFrames > FrameTimeStatistics.MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException(
                    "measurement frame count is outside the bounded range");
        }
        assetLock = normalize(assetLock, "assetLock");
        outputDirectory = normalize(outputDirectory, "outputDirectory");
    }

    static GraphicsBenchmarkManualOptions parse(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        String preset = null;
        String width = null;
        String height = null;
        String renderScale = null;
        String warmUpFrames = null;
        String measurementFrames = null;
        String assetLock = null;
        String outputDirectory = null;

        for (int index = 0; index < arguments.length; index++) {
            String argument = Objects.requireNonNull(arguments[index], "argument");
            switch (argument) {
                case "--preset" -> preset = requireValue(arguments, ++index, argument, preset);
                case "--width" -> width = requireValue(arguments, ++index, argument, width);
                case "--height" -> height = requireValue(arguments, ++index, argument, height);
                case "--render-scale" ->
                        renderScale = requireValue(arguments, ++index, argument, renderScale);
                case "--warm-up-frames" ->
                        warmUpFrames = requireValue(arguments, ++index, argument, warmUpFrames);
                case "--measurement-frames" ->
                        measurementFrames =
                                requireValue(arguments, ++index, argument, measurementFrames);
                case "--asset-lock" ->
                        assetLock = requireValue(arguments, ++index, argument, assetLock);
                case "--output-dir" ->
                        outputDirectory =
                                requireValue(arguments, ++index, argument, outputDirectory);
                default -> throw new IllegalArgumentException("unknown benchmark argument");
            }
        }
        if (preset == null
                || width == null
                || height == null
                || renderScale == null
                || warmUpFrames == null
                || measurementFrames == null
                || assetLock == null
                || outputDirectory == null) {
            throw new IllegalArgumentException("missing benchmark argument");
        }

        return new GraphicsBenchmarkManualOptions(
                parsePreset(preset),
                parseInteger(width, "width"),
                parseInteger(height, "height"),
                parseRenderScale(renderScale),
                parseInteger(warmUpFrames, "warm-up frame count"),
                parseInteger(measurementFrames, "measurement frame count"),
                Path.of(assetLock),
                Path.of(outputDirectory));
    }

    private static GraphicsQualityPreset parsePreset(String value) {
        try {
            return GraphicsQualityPreset.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid benchmark preset", exception);
        }
    }

    private static int parseInteger(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid " + field, exception);
        }
    }

    private static double parseRenderScale(String value) {
        try {
            return new BigDecimal(value).doubleValue();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid render scale", exception);
        }
    }

    private static String requireValue(
            String[] arguments, int index, String option, String previous) {
        if (previous != null
                || index >= arguments.length
                || arguments[index] == null
                || arguments[index].isBlank()
                || arguments[index].startsWith("--")) {
            throw new IllegalArgumentException("invalid value for " + option);
        }
        return arguments[index];
    }

    private static Path normalize(Path path, String field) {
        return Objects.requireNonNull(path, field).toAbsolutePath().normalize();
    }
}
