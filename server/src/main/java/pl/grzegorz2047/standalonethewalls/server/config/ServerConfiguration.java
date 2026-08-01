package pl.grzegorz2047.standalonethewalls.server.config;

import java.util.Objects;

/** Immutable, validated configuration for the first headless server runtime. */
public record ServerConfiguration(
        String name,
        int tickRate,
        int reliablePort,
        int realtimePort,
        int maximumPlayers) {
    public static final int MINIMUM_TICK_RATE = 10;
    public static final int MAXIMUM_TICK_RATE = 60;
    public static final int MAXIMUM_SUPPORTED_PLAYERS = 40;

    public ServerConfiguration {
        name = Objects.requireNonNull(name, "name").strip();
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 1 || nameLength > 64) {
            throw new IllegalArgumentException("name must contain between 1 and 64 code points");
        }
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("name cannot contain control characters");
        }
        if (tickRate < MINIMUM_TICK_RATE || tickRate > MAXIMUM_TICK_RATE) {
            throw new IllegalArgumentException(
                    "tickRate must be between " + MINIMUM_TICK_RATE + " and " + MAXIMUM_TICK_RATE);
        }
        requirePort(reliablePort, "reliablePort");
        requirePort(realtimePort, "realtimePort");
        if (reliablePort == realtimePort) {
            throw new IllegalArgumentException("reliablePort and realtimePort must be different");
        }
        if (maximumPlayers < 1 || maximumPlayers > MAXIMUM_SUPPORTED_PLAYERS) {
            throw new IllegalArgumentException(
                    "maximumPlayers must be between 1 and " + MAXIMUM_SUPPORTED_PLAYERS);
        }
    }

    public static ServerConfiguration defaults() {
        return new ServerConfiguration("Sunderfront Server", 20, 27420, 27421, 40);
    }

    public long tickPeriodNanos() {
        return 1_000_000_000L / tickRate;
    }

    private static void requirePort(int port, String field) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(field + " must be between 1 and 65535");
        }
    }
}
