package pl.grzegorz2047.standalonethewalls.domain.match;

import java.util.Objects;

/** Complete deterministic lifecycle state required by the first match-state slice. */
public record MatchState(
        MatchPhase phase,
        long ticksRemaining,
        int connectedPlayers,
        long roundNumber,
        MatchResult result) {

    public MatchState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(result, "result");
        if (ticksRemaining < 0L) {
            throw new IllegalArgumentException("ticksRemaining cannot be negative");
        }
        if (connectedPlayers < 0) {
            throw new IllegalArgumentException("connectedPlayers cannot be negative");
        }
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be at least 1");
        }
        if (isUntimed(phase) && ticksRemaining != 0L) {
            throw new IllegalArgumentException("untimed phase must have zero ticksRemaining");
        }
        if (!isUntimed(phase) && ticksRemaining < 1L) {
            throw new IllegalArgumentException("timed phase must have at least one tick remaining");
        }
        boolean resultAllowed = phase == MatchPhase.RESULTS || phase == MatchPhase.RESETTING;
        if (resultAllowed == (result == MatchResult.NONE)) {
            throw new IllegalArgumentException("result must exist only during results/resetting");
        }
    }

    public static MatchState initial() {
        return new MatchState(MatchPhase.BOOT, 0L, 0, 1L, MatchResult.NONE);
    }

    private static boolean isUntimed(MatchPhase phase) {
        return phase == MatchPhase.BOOT
                || phase == MatchPhase.LOADING_MAP
                || phase == MatchPhase.WAITING_FOR_PLAYERS;
    }
}
