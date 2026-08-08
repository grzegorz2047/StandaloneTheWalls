package pl.grzegorz2047.standalonethewalls.server.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;

class LobbyMatchProtocolAdapterTest {
    @Test
    void mapsPublishedPhasesWithoutEnumOrdinals() {
        LobbyMatchPhaseSnapshot waiting =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(
                                MatchPhase.WAITING_FOR_PLAYERS,
                                0L,
                                Optional.of(CountdownCancellationReason.INSUFFICIENT_PLAYERS)));
        LobbyMatchPhaseSnapshot countdown =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(MatchPhase.START_COUNTDOWN, 17L, Optional.empty()));
        LobbyMatchPhaseSnapshot preparation =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(MatchPhase.PREPARATION, 400L, Optional.empty()));
        LobbyMatchPhaseSnapshot opening =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(MatchPhase.WALLS_OPENING, 50L, Optional.empty()));
        LobbyMatchPhaseSnapshot combat =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(MatchPhase.OPEN_COMBAT, 8_400L, Optional.empty()));

        assertThat(waiting.phase()).isEqualTo(LobbyMatchPhase.WAITING_FOR_PLAYERS);
        assertThat(waiting.authoritativeTick()).isEqualTo(LobbyMatchSnapshot.BEFORE_FIRST_TICK);
        assertThat(waiting.cancellationReason())
                .isEqualTo(LobbyCountdownCancellationReason.INSUFFICIENT_PLAYERS);
        assertThat(countdown.phase()).isEqualTo(LobbyMatchPhase.START_COUNTDOWN);
        assertThat(countdown.ticksRemaining()).isEqualTo(17L);
        assertThat(countdown.cancellationReason()).isEqualTo(LobbyCountdownCancellationReason.NONE);
        assertThat(preparation.phase()).isEqualTo(LobbyMatchPhase.PREPARATION);
        assertThat(preparation.ticksRemaining()).isEqualTo(400L);
        assertThat(opening.phase()).isEqualTo(LobbyMatchPhase.WALLS_OPENING);
        assertThat(opening.ticksRemaining()).isEqualTo(50L);
        assertThat(combat.phase()).isEqualTo(LobbyMatchPhase.OPEN_COMBAT);
        assertThat(combat.ticksRemaining()).isEqualTo(8_400L);
    }

    @Test
    void mapsLobbyNotReadyCancellationReason() {
        LobbyMatchPhaseSnapshot snapshot =
                LobbyMatchProtocolAdapter.toProtocol(
                        snapshot(
                                MatchPhase.WAITING_FOR_PLAYERS,
                                0L,
                                Optional.of(CountdownCancellationReason.LOBBY_NOT_READY)));

        assertThat(snapshot.cancellationReason())
                .isEqualTo(LobbyCountdownCancellationReason.LOBBY_NOT_READY);
    }

    private static LobbyMatchSnapshot snapshot(
            MatchPhase phase,
            long ticksRemaining,
            Optional<CountdownCancellationReason> cancellationReason) {
        return new LobbyMatchSnapshot(
                3L,
                4L,
                LobbyMatchSnapshot.BEFORE_FIRST_TICK,
                phase,
                ticksRemaining,
                2,
                1L,
                MatchResult.NONE,
                cancellationReason);
    }
}
