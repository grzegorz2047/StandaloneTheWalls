package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;

/** Immutable authoritative phase/countdown state carried by reliable lobby messages. */
public record LobbyMatchPhaseSnapshot(
        long revision,
        long rosterRevision,
        long authoritativeTick,
        LobbyMatchPhase phase,
        long ticksRemaining,
        int connectedPlayers,
        long roundNumber,
        LobbyCountdownCancellationReason cancellationReason) {
    public static final long BEFORE_FIRST_TICK = -1L;

    public LobbyMatchPhaseSnapshot {
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
        if (connectedPlayers < 0 || connectedPlayers > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new IllegalArgumentException("connectedPlayers is outside the supported range");
        }
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be at least 1");
        }
        Objects.requireNonNull(cancellationReason, "cancellationReason");

        if (phase == LobbyMatchPhase.WAITING_FOR_PLAYERS && ticksRemaining != 0L) {
            throw new IllegalArgumentException("waiting phase must have zero ticksRemaining");
        }
        if (phase != LobbyMatchPhase.WAITING_FOR_PLAYERS && ticksRemaining < 1L) {
            throw new IllegalArgumentException("timed phase must have at least one tick remaining");
        }
        if (phase != LobbyMatchPhase.WAITING_FOR_PLAYERS
                && cancellationReason != LobbyCountdownCancellationReason.NONE) {
            throw new IllegalArgumentException(
                    "countdown cancellation reason is valid only while waiting for players");
        }
    }
}
