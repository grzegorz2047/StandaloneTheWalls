package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsBenchmarkManualMainTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @TempDir Path tempDirectory;

    @Test
    void preparesExactSessionConfigFromBuildAndAssetIdentity() throws IOException {
        Path assetLock = tempDirectory.resolve("assets.lock.json");
        assertThat(
                        Files.writeString(
                                assetLock, "{\"packs\":[],\"schema\":1}", StandardCharsets.UTF_8))
                .isEqualTo(assetLock);
        Path output = tempDirectory.resolve("reports");
        String[] arguments = arguments(assetLock, output);

        GraphicsBenchmarkManualMain.PreparedRun prepared =
                GraphicsBenchmarkManualMain.prepare(arguments, Optional.of(COMMIT));
        GraphicsBenchmarkAssetIdentity identity =
                GraphicsBenchmarkAssetIdentity.fromLock(assetLock);

        assertThat(prepared.options().preset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(prepared.config().repositoryCommit()).isEqualTo(COMMIT);
        assertThat(prepared.config().compatibilityKey()).isEqualTo(identity.compatibilityKey());
        assertThat(prepared.config().measuredPreset()).isEqualTo(GraphicsQualityPreset.MEDIUM);
        assertThat(prepared.config().width()).isEqualTo(1920);
        assertThat(prepared.config().height()).isEqualTo(1080);
        assertThat(prepared.config().renderScale()).isEqualTo(1.0d);
        assertThat(prepared.config().warmUpFrameCount()).isEqualTo(120);
        assertThat(prepared.config().measurementFrameCount()).isEqualTo(600);
        assertThat(prepared.options().outputDirectory())
                .isEqualTo(output.toAbsolutePath().normalize());
    }

    @Test
    void unavailableBuildProvenanceFailsBeforeAssetIdentityLookup() {
        Path missingAssetLock = tempDirectory.resolve("missing.lock");
        String[] arguments = arguments(missingAssetLock, tempDirectory.resolve("reports"));

        assertThatIllegalStateException()
                .isThrownBy(() -> GraphicsBenchmarkManualMain.prepare(arguments, Optional.empty()));
    }

    private static String[] arguments(Path assetLock, Path outputDirectory) {
        return new String[] {
            "--preset",
            "medium",
            "--width",
            "1920",
            "--height",
            "1080",
            "--warm-up-frames",
            "120",
            "--measurement-frames",
            "600",
            "--asset-lock",
            assetLock.toString(),
            "--output-dir",
            outputDirectory.toString()
        };
    }
}
