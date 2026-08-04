package pl.grzegorz2047.standalonethewalls.server.lobby;

import java.util.Objects;
import java.util.Optional;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchResult;

/** Immutable server-owned view of the authoritative match phase visible to lobby clients. */
public record LobbyMatchSnapshot(
        long revision,
        long rosterRevision,
        long authoritativeTick,
        MatchPhase phase,
        long ticksRemaining,
        int connectedPlayers,
        long roundNumber,
        MatchResult result,
        Optional<CountdownCancellationReason> cancellationReason) {
    public static final long BEFORE_FIRST_TICK = -1L;

    public LobbyMatchSnapshot {
        if (revision < 0L) {
            throw new IllegalArgumentException("match snapshot revision cannot be negative");
        }
        if (rosterRevision < 0L) {
            throw new IllegalArgumentException("roster revision cannot be negative");
        }
        if (authoritativeTick < BEFORE_FIRST_TICK) {
            throw new IllegalArgumentException("authoritativeTick is outside the supported range");
        }
        Objects.requireNonNull(phase, "phase");
        if (ticksRemaining < 0L) {
            throw new IllegalArgumentException("ticksRemaining cannot be negative");
        }
        if (connectedPlayers < 0) {
            throw new IllegalArgumentException("connectedPlayers cannot be negative");
        }
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be at least 1");
        }
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(cancellationReason, "cancellationReason");

        boolean untimed =
                phase == MatchPhase.BOOT
                        || phase == MatchPhase.LOADING_MAP
                        || phase == MatchPhase.WAITING_FOR_PLAYERS;
        if (untimed && ticksRemaining != 0L) {
            throw new IllegalArgumentException("untimed phase must have zero ticksRemaining");
        }
        if (!untimed && ticksRemaining < 1L) {
            throw new IllegalArgumentException("timed phase must have at least one tick remaining");
        }
        boolean resultAllowed = phase == MatchPhase.RESULTS || phase == MatchPhase.RESETTING;
        if (resultAllowed == (result == MatchResult.NONE)) {
            throw new IllegalArgumentException("result must exist only during results/resetting");
        }
        if (cancellationReason.isPresent() && phase != MatchPhase.WAITING_FOR_PLAYERS) {
            throw new IllegalArgumentException(
                    "countdown cancellation reason is valid only while waiting for players");
        }
    }
}
