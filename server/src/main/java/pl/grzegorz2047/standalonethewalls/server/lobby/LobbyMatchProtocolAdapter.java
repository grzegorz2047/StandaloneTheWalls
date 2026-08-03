package pl.grzegorz2047.standalonethewalls.server.lobby;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;

/** Explicit domain-to-wire mapping for the lobby-to-preparation lifecycle slice. */
final class LobbyMatchProtocolAdapter {
    private LobbyMatchProtocolAdapter() {
        throw new AssertionError("No instances");
    }

    static LobbyMatchPhaseSnapshot toProtocol(LobbyMatchSnapshot snapshot) {
        LobbyMatchSnapshot source = Objects.requireNonNull(snapshot, "snapshot");
        LobbyMatchPhase phase =
                switch (source.phase()) {
                    case WAITING_FOR_PLAYERS -> LobbyMatchPhase.WAITING_FOR_PLAYERS;
                    case START_COUNTDOWN -> LobbyMatchPhase.START_COUNTDOWN;
                    case PREPARATION -> LobbyMatchPhase.PREPARATION;
                    case BOOT,
                            LOADING_MAP,
                            WALLS_OPENING,
                            OPEN_COMBAT,
                            DEATHMATCH_TRANSITION,
                            DEATHMATCH,
                            RESULTS,
                            RESETTING ->
                            throw new IllegalArgumentException(
                                    "match phase is outside the lobby protocol slice");
                };
        LobbyCountdownCancellationReason cancellationReason =
                source.cancellationReason()
                        .map(LobbyMatchProtocolAdapter::cancellationReason)
                        .orElse(LobbyCountdownCancellationReason.NONE);
        return new LobbyMatchPhaseSnapshot(
                source.revision(),
                source.rosterRevision(),
                source.authoritativeTick(),
                phase,
                source.ticksRemaining(),
                source.connectedPlayers(),
                source.roundNumber(),
                cancellationReason);
    }

    private static LobbyCountdownCancellationReason cancellationReason(
            CountdownCancellationReason reason) {
        return switch (Objects.requireNonNull(reason, "reason")) {
            case INSUFFICIENT_PLAYERS ->
                    LobbyCountdownCancellationReason.INSUFFICIENT_PLAYERS;
            case LOBBY_NOT_READY -> LobbyCountdownCancellationReason.LOBBY_NOT_READY;
        };
    }
}
