package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphicsQualityStateStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void missingStateIsNormalFirstRunCondition() throws Exception {
        GraphicsQualityStateStore store =
                new GraphicsQualityStateStore(temporaryDirectory.resolve("fresh"));

        assertThat(store.load()).isEmpty();
    }

    @Test
    void saveLoadRoundTripsCanonicalStateAndReplacesPreviousValue() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("client-data");
        GraphicsQualityStateStore store = new GraphicsQualityStateStore(dataDirectory);
        GraphicsBenchmarkCompatibilityKey key =
                new GraphicsBenchmarkCompatibilityKey("core=assets", "7", "first-run", 2);
        GraphicsQualityState initial =
                new GraphicsQualityState(
                        key,
                        GraphicsQualityPreset.MEDIUM,
                        Optional.of(GraphicsQualityPreset.LOW));

        store.save(initial);

        assertThat(store.load()).contains(initial);
        assertThat(Files.readString(store.stateFile(), StandardCharsets.UTF_8))
                .isEqualTo(
                        "schemaVersion=1\n"
                                + "assetPackId=core=assets\n"
                                + "assetPackVersion=7\n"
                                + "scenarioId=first-run\n"
                                + "scenarioVersion=2\n"
                                + "recommendedPreset=MEDIUM\n"
                                + "manualOverride=LOW\n");

        GraphicsQualityState replacement =
                new GraphicsQualityState(key, GraphicsQualityPreset.HIGH, Optional.empty());
        store.save(replacement);

        assertThat(store.load()).contains(replacement);
        try (var paths = Files.list(dataDirectory)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(GraphicsQualityStateStore.FILE_NAME);
        }
    }

    @Test
    void rejectsDuplicateUnknownMalformedAndOversizedState() throws Exception {
        GraphicsQualityStateStore store = new GraphicsQualityStateStore(temporaryDirectory);
        String valid =
                "schemaVersion=1\n"
                        + "assetPackId=core\n"
                        + "assetPackVersion=7\n"
                        + "scenarioId=first-run\n"
                        + "scenarioVersion=2\n"
                        + "recommendedPreset=MEDIUM\n"
                        + "manualOverride=NONE\n";

        assertMalformed(
                store,
                valid.replace(
                        "manualOverride=NONE\n",
                        "recommendedPreset=LOW\nmanualOverride=NONE\n"));
        assertMalformed(store, valid.replace("manualOverride=NONE", "futureField=NONE"));
        assertMalformed(store, valid.substring(0, valid.length() - 1));
        assertMalformed(store, valid.replace("recommendedPreset=MEDIUM", "recommendedPreset=ULTRA"));
        assertMalformed(store, valid.replace("schemaVersion=1", "schemaVersion=2"));

        Files.writeString(store.stateFile(), "x".repeat(4_097), StandardCharsets.UTF_8);
        assertThatThrownBy(store::load)
                .isInstanceOf(GraphicsQualityStateStore.MalformedStateException.class);
    }

    @Test
    void compatibilityKeyRejectsUnsafeMetadataAndInvalidScenarioVersion() {
        assertThatThrownBy(
                        () -> new GraphicsBenchmarkCompatibilityKey("core\nassets", "7", "scene", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GraphicsBenchmarkCompatibilityKey("core", "7", "scene", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertMalformed(GraphicsQualityStateStore store, String content)
            throws Exception {
        Files.writeString(store.stateFile(), content, StandardCharsets.UTF_8);
        assertThatThrownBy(store::load)
                .isInstanceOf(GraphicsQualityStateStore.MalformedStateException.class);
    }
}
