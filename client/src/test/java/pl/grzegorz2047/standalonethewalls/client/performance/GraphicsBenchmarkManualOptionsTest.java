package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkManualOptionsTest {
    @TempDir Path tempDirectory;

    @Test
    void parsesExplicitPresetRenderScaleBenchmarkShapes() {
        Path assetLock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        assertScale("low", "1280", "720", "0.67", assetLock, output, 0.67d);
        assertScale("low", "1280", "720", "0.75", assetLock, output, 0.75d);
        assertScale("low", "1280", "720", "1.0", assetLock, output, 1.0d);
        assertScale("medium", "1920", "1080", "0.75", assetLock, output, 0.75d);
        assertScale("MEDIUM", "1920", "1080", "1.0", assetLock, output, 1.0d);
        assertScale("high", "1920", "1080", "0.85", assetLock, output, 0.85d);
        assertScale("HIGH", "1920", "1080", "1.0", assetLock, output, 1.0d);
    }

    @Test
    void retainsExplicitFramesAndNormalizedPaths() {
        Path assetLock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        GraphicsBenchmarkManualOptions options =
                GraphicsBenchmarkManualOptions.parse(
                        arguments(
                                "medium", "1920", "1080", "0.75", "240", "900", assetLock, output));

        assertThat(options.preset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(options.width()).isEqualTo(1920);
        assertThat(options.height()).isEqualTo(1080);
        assertThat(options.renderScale()).isEqualTo(0.75d);
        assertThat(options.warmUpFrames()).isEqualTo(240);
        assertThat(options.measurementFrames()).isEqualTo(900);
        assertThat(options.assetLock()).isEqualTo(assetLock.toAbsolutePath().normalize());
        assertThat(options.outputDirectory()).isEqualTo(output.toAbsolutePath().normalize());
    }

    @Test
    void rejectsMissingDuplicateAndUnknownArguments() {
        assertInvalid(new String[] {"--preset", "low"});

        Path lock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");
        String[] valid = arguments("low", "1280", "720", "0.75", "1", "1", lock, output);
        String[] duplicate = java.util.Arrays.copyOf(valid, valid.length + 2);
        duplicate[valid.length] = "--render-scale";
        duplicate[valid.length + 1] = "1.0";
        assertInvalid(duplicate);

        assertInvalid(new String[] {"--unknown", "x"});
    }

    @Test
    void rejectsMalformedAndOutOfPresetRenderScales() {
        Path lock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        assertRejectedScale("low", "0.66", lock, output);
        assertRejectedScale("medium", "0.749", lock, output);
        assertRejectedScale("high", "0.75", lock, output);
        assertRejectedScale("high", "1.01", lock, output);
        assertRejectedScale("low", "0", lock, output);
        assertRejectedScale("low", "NaN", lock, output);
        assertRejectedScale("low", "Infinity", lock, output);
        assertRejectedScale("low", "not-a-number", lock, output);
    }

    @Test
    void rejectsInvalidPresetResolutionAndFrameBounds() {
        Path lock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        assertInvalid(arguments("ultra", "1280", "720", "1.0", "1", "1", lock, output));
        assertInvalid(arguments("low", "319", "720", "1.0", "1", "1", lock, output));
        assertInvalid(arguments("low", "1280", "4321", "1.0", "1", "1", lock, output));
        assertInvalid(arguments("low", "1280", "720", "1.0", "-1", "1", lock, output));
        assertInvalid(arguments("low", "1280", "720", "1.0", "1", "0", lock, output));
        assertInvalid(arguments("low", "abc", "720", "1.0", "1", "1", lock, output));
    }

    private static void assertInvalid(String[] arguments) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GraphicsBenchmarkManualOptions.parse(arguments));
    }

    private static void assertRejectedScale(String preset, String scale, Path lock, Path output) {
        assertInvalid(arguments(preset, "1280", "720", scale, "1", "1", lock, output));
    }

    private static void assertScale(
            String preset,
            String width,
            String height,
            String scale,
            Path assetLock,
            Path outputDirectory,
            double expectedScale) {
        GraphicsBenchmarkManualOptions options =
                GraphicsBenchmarkManualOptions.parse(
                        arguments(
                                preset,
                                width,
                                height,
                                scale,
                                "120",
                                "600",
                                assetLock,
                                outputDirectory));
        assertThat(options.renderScale()).isEqualTo(expectedScale);
    }

    private static String[] arguments(
            String preset,
            String width,
            String height,
            String renderScale,
            String warmUpFrames,
            String measurementFrames,
            Path assetLock,
            Path outputDirectory) {
        return new String[] {
            "--preset",
            preset,
            "--width",
            width,
            "--height",
            height,
            "--render-scale",
            renderScale,
            "--warm-up-frames",
            warmUpFrames,
            "--measurement-frames",
            measurementFrames,
            "--asset-lock",
            assetLock.toString(),
            "--output-dir",
            outputDirectory.toString()
        };
    }
}
