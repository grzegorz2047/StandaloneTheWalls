package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.util.Objects;

/** Correlated authoritative result for one lobby command. */
public record LobbyCommandResult(long requestId, long revision, LobbyCommandOutcome outcome) {
    public LobbyCommandResult {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(outcome, "outcome");
    }
}
