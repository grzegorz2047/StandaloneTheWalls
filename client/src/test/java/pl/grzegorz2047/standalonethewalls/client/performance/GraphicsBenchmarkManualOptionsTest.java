package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkManualOptionsTest {
    @TempDir Path tempDirectory;

    @Test
    void parsesExplicitLowAndMediumBenchmarkShapes() {
        Path assetLock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        GraphicsBenchmarkManualOptions low =
                GraphicsBenchmarkManualOptions.parse(
                        arguments("low", "1280", "720", "120", "600", assetLock, output));
        GraphicsBenchmarkManualOptions medium =
                GraphicsBenchmarkManualOptions.parse(
                        arguments("MEDIUM", "1920", "1080", "240", "900", assetLock, output));

        assertThat(low.preset()).isEqualTo(GraphicsQualityPreset.LOW);
        assertThat(low.width()).isEqualTo(1280);
        assertThat(low.height()).isEqualTo(720);
        assertThat(low.warmUpFrames()).isEqualTo(120);
        assertThat(low.measurementFrames()).isEqualTo(600);
        assertThat(low.assetLock()).isEqualTo(assetLock.toAbsolutePath().normalize());
        assertThat(low.outputDirectory()).isEqualTo(output.toAbsolutePath().normalize());

        assertThat(medium.preset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(medium.width()).isEqualTo(1920);
        assertThat(medium.height()).isEqualTo(1080);
        assertThat(medium.warmUpFrames()).isEqualTo(240);
        assertThat(medium.measurementFrames()).isEqualTo(900);
    }

    @Test
    void rejectsMissingDuplicateUnknownAndMetadataOnlyRenderScaleArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        new String[] {"--preset", "low"}));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> {
                            String[] valid =
                                    arguments(
                                            "low",
                                            "1280",
                                            "720",
                                            "1",
                                            "1",
                                            tempDirectory.resolve("assets.lock.json"),
                                            tempDirectory.resolve("reports"));
                            String[] duplicate = java.util.Arrays.copyOf(valid, valid.length + 2);
                            duplicate[valid.length] = "--preset";
                            duplicate[valid.length + 1] = "high";
                            GraphicsBenchmarkManualOptions.parse(duplicate);
                        });
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> GraphicsBenchmarkManualOptions.parse(new String[] {"--unknown", "x"}));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        new String[] {"--render-scale", "0.75"}));
    }

    @Test
    void rejectsInvalidPresetResolutionAndFrameBounds() {
        Path lock = tempDirectory.resolve("assets.lock.json");
        Path output = tempDirectory.resolve("reports");

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("ultra", "1280", "720", "1", "1", lock, output)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("low", "319", "720", "1", "1", lock, output)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("low", "1280", "4321", "1", "1", lock, output)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("low", "1280", "720", "-1", "1", lock, output)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("low", "1280", "720", "1", "0", lock, output)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                GraphicsBenchmarkManualOptions.parse(
                                        arguments("low", "abc", "720", "1", "1", lock, output)));
    }

    private static String[] arguments(
            String preset,
            String width,
            String height,
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
