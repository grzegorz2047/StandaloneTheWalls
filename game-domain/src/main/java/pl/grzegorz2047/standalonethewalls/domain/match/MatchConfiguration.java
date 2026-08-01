package pl.grzegorz2047.standalonethewalls.domain.match;

/** Immutable tick-based match timing configuration. */
public record MatchConfiguration(
        int minimumPlayers,
        long startCountdownTicks,
        long preparationTicks,
        long wallsOpeningTicks,
        long openCombatTicks,
        long deathmatchTransitionTicks,
        long deathmatchTicks,
        long resultsTicks,
        long resettingTicks) {

    public MatchConfiguration {
        if (minimumPlayers < 1) {
            throw new IllegalArgumentException("minimumPlayers must be at least 1");
        }
        requirePositive(startCountdownTicks, "startCountdownTicks");
        requirePositive(preparationTicks, "preparationTicks");
        requirePositive(wallsOpeningTicks, "wallsOpeningTicks");
        requirePositive(openCombatTicks, "openCombatTicks");
        requirePositive(deathmatchTransitionTicks, "deathmatchTransitionTicks");
        requirePositive(deathmatchTicks, "deathmatchTicks");
        requirePositive(resultsTicks, "resultsTicks");
        requirePositive(resettingTicks, "resettingTicks");
    }

    public static MatchConfiguration defaults(int ticksPerSecond) {
        if (ticksPerSecond < 1) {
            throw new IllegalArgumentException("ticksPerSecond must be at least 1");
        }
        return new MatchConfiguration(
                2,
                60L * ticksPerSecond,
                10L * 60L * ticksPerSecond,
                5L * ticksPerSecond,
                7L * 60L * ticksPerSecond,
                5L * ticksPerSecond,
                5L * 60L * ticksPerSecond,
                15L * ticksPerSecond,
                2L * ticksPerSecond);
    }

    public long durationFor(MatchPhase phase) {
        return switch (phase) {
            case START_COUNTDOWN -> startCountdownTicks;
            case PREPARATION -> preparationTicks;
            case WALLS_OPENING -> wallsOpeningTicks;
            case OPEN_COMBAT -> openCombatTicks;
            case DEATHMATCH_TRANSITION -> deathmatchTransitionTicks;
            case DEATHMATCH -> deathmatchTicks;
            case RESULTS -> resultsTicks;
            case RESETTING -> resettingTicks;
            case BOOT, LOADING_MAP, WAITING_FOR_PLAYERS -> 0L;
        };
    }

    private static void requirePositive(long value, String field) {
        if (value < 1L) {
            throw new IllegalArgumentException(field + " must be at least 1 tick");
        }
    }
}
