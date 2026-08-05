package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.util.List;
import java.util.Objects;

/** Deterministically ordered authoritative player states for one preparation simulation tick. */
public record PreparationWorldSnapshot(
        long roundNumber, long authoritativeTick, List<PreparationPlayerSnapshot> players) {
    public static final int MAXIMUM_PLAYERS = 40;

    public PreparationWorldSnapshot {
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick cannot be negative");
        }
        List<PreparationPlayerSnapshot> copied =
                List.copyOf(Objects.requireNonNull(players, "players"));
        if (copied.isEmpty() || copied.size() > MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("players size is outside [1, 40]");
        }
        String previous = null;
        for (PreparationPlayerSnapshot player : copied) {
            PreparationPlayerSnapshot current = Objects.requireNonNull(player, "player");
            String playerId = current.playerId().value();
            if (previous != null && previous.compareTo(playerId) >= 0) {
                throw new IllegalArgumentException(
                        "players must be unique and strictly ordered by playerId");
            }
            previous = playerId;
        }
        players = copied;
    }
}
