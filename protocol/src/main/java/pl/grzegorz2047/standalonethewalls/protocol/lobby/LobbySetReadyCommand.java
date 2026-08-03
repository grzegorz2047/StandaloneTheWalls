package pl.grzegorz2047.standalonethewalls.protocol.lobby;

/** Client intent to set readiness for the authenticated session. */
public record LobbySetReadyCommand(long requestId, boolean ready) {
    public LobbySetReadyCommand {
        if (requestId < 1L) {
            throw new IllegalArgumentException("requestId must be positive");
        }
    }
}
