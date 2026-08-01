package pl.grzegorz2047.standalonethewalls.server.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Strict properties loader. Unknown keys and malformed numbers fail closed. */
public final class ServerConfigurationLoader {
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "server.name",
            "server.tick-rate",
            "server.reliable-port",
            "server.realtime-port",
            "server.maximum-players");

    private ServerConfigurationLoader() {
        throw new AssertionError("No instances");
    }

    public static ServerConfiguration load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    public static ServerConfiguration load(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Properties properties = new Properties();
        properties.load(reader);
        for (String key : properties.stringPropertyNames()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown server configuration key: " + key);
            }
        }

        ServerConfiguration defaults = ServerConfiguration.defaults();
        return new ServerConfiguration(
                properties.getProperty("server.name", defaults.name()),
                integer(properties, "server.tick-rate", defaults.tickRate()),
                integer(properties, "server.reliable-port", defaults.reliablePort()),
                integer(properties, "server.realtime-port", defaults.realtimePort()),
                integer(properties, "server.maximum-players", defaults.maximumPlayers()));
    }

    private static int integer(Properties properties, String key, int defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.strip();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a base-10 integer", exception);
        }
    }
}
