package pl.grzegorz2047.standalonethewalls.client.performance;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Strict versioned local persistence for first-run graphics quality state. */
public final class GraphicsQualityStateStore {
    public static final String FILE_NAME = "graphics-quality-state.txt";
    private static final int SCHEMA_VERSION = 1;
    private static final long MAXIMUM_FILE_BYTES = 4_096L;
    private static final String NO_OVERRIDE = "NONE";
    private static final int EXPECTED_FIELD_COUNT = 7;

    private final Path dataDirectory;
    private final Path stateFile;

    public GraphicsQualityStateStore(Path dataDirectory) {
        this.dataDirectory =
                Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.stateFile = this.dataDirectory.resolve(FILE_NAME);
    }

    public Optional<GraphicsQualityState> load() throws IOException {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        long size = Files.size(stateFile);
        if (size < 1L || size > MAXIMUM_FILE_BYTES) {
            throw new MalformedStateException(
                    "graphics quality state size is outside the bounded range");
        }
        try {
            return Optional.of(parse(Files.readString(stateFile, StandardCharsets.UTF_8)));
        } catch (MalformedInputException exception) {
            throw new MalformedStateException(
                    "graphics quality state is not valid UTF-8", exception);
        }
    }

    public void save(GraphicsQualityState state) throws IOException {
        Objects.requireNonNull(state, "state");
        Files.createDirectories(dataDirectory);
        Path temporary = Files.createTempFile(dataDirectory, ".graphics-quality-state-", ".tmp");
        try {
            Files.writeString(temporary, serialize(state), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        stateFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path stateFile() {
        return stateFile;
    }

    static String serialize(GraphicsQualityState state) {
        Objects.requireNonNull(state, "state");
        GraphicsBenchmarkCompatibilityKey key = state.compatibilityKey();
        return "schemaVersion="
                + SCHEMA_VERSION
                + "\nassetPackId="
                + key.assetPackId()
                + "\nassetPackVersion="
                + key.assetPackVersion()
                + "\nscenarioId="
                + key.scenarioId()
                + "\nscenarioVersion="
                + key.scenarioVersion()
                + "\nrecommendedPreset="
                + state.recommendedPreset().name()
                + "\nmanualOverride="
                + state.manualOverride().map(Enum::name).orElse(NO_OVERRIDE)
                + "\n";
    }

    static GraphicsQualityState parse(String content) throws MalformedStateException {
        Objects.requireNonNull(content, "content");
        if (content.indexOf('\r') >= 0 || !content.endsWith("\n")) {
            throw new MalformedStateException(
                    "graphics quality state must use canonical LF lines");
        }
        String body = content.substring(0, content.length() - 1);
        String[] lines = body.split("\n", -1);
        if (lines.length != EXPECTED_FIELD_COUNT) {
            throw new MalformedStateException("graphics quality state field count is invalid");
        }

        Map<String, String> fields = new HashMap<>();
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator < 1) {
                throw new MalformedStateException("graphics quality state line is invalid");
            }
            String key = line.substring(0, separator);
            if (!isKnownField(key)) {
                throw new MalformedStateException(
                        "graphics quality state contains an unknown field");
            }
            if (fields.putIfAbsent(key, line.substring(separator + 1)) != null) {
                throw new MalformedStateException(
                        "graphics quality state contains a duplicate field");
            }
        }

        try {
            if (Integer.parseInt(required(fields, "schemaVersion")) != SCHEMA_VERSION) {
                throw new MalformedStateException(
                        "graphics quality state schema version is unsupported");
            }
            GraphicsBenchmarkCompatibilityKey compatibilityKey =
                    new GraphicsBenchmarkCompatibilityKey(
                            required(fields, "assetPackId"),
                            required(fields, "assetPackVersion"),
                            required(fields, "scenarioId"),
                            Integer.parseInt(required(fields, "scenarioVersion")));
            GraphicsQualityPreset recommendedPreset =
                    GraphicsQualityPreset.valueOf(required(fields, "recommendedPreset"));
            String overrideValue = required(fields, "manualOverride");
            Optional<GraphicsQualityPreset> manualOverride =
                    NO_OVERRIDE.equals(overrideValue)
                            ? Optional.empty()
                            : Optional.of(GraphicsQualityPreset.valueOf(overrideValue));
            return new GraphicsQualityState(compatibilityKey, recommendedPreset, manualOverride);
        } catch (MalformedStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MalformedStateException(
                    "graphics quality state value is invalid", exception);
        }
    }

    private static String required(Map<String, String> fields, String key)
            throws MalformedStateException {
        String value = fields.get(key);
        if (value == null) {
            throw new MalformedStateException(
                    "graphics quality state is missing a required field");
        }
        return value;
    }

    private static boolean isKnownField(String key) {
        return switch (key) {
            case "schemaVersion",
                    "assetPackId",
                    "assetPackVersion",
                    "scenarioId",
                    "scenarioVersion",
                    "recommendedPreset",
                    "manualOverride" -> true;
            default -> false;
        };
    }

    public static final class MalformedStateException extends IOException {
        private static final long serialVersionUID = 1L;

        public MalformedStateException(String message) {
            super(message);
        }

        public MalformedStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
