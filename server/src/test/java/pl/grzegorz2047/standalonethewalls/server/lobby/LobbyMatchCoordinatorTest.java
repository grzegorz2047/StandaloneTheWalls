package pl.grzegorz2047.standalonethewalls.server.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchEvent.CountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;

class LobbyMatchCoordinatorTest {
    private static final LobbyConfiguration LOBBY = LobbyConfiguration.standard();
    private static final MatchConfiguration MATCH =
            new MatchConfiguration(2, 2, 2, 1, 2, 1, 2, 1, 1);

    @Test
    void startsCancelsAndRestartsFromAuthoritativeRoster() {
        LobbyMatchCoordinator coordinator = new LobbyMatchCoordinator(LOBBY, MATCH);

        assertThat(coordinator.snapshot().revision()).isZero();
        assertThat(coordinator.snapshot().phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(coordinator.snapshot().rosterRevision()).isZero();

        LobbyMatchSnapshot started =
                coordinator.updateRoster(twoPlayers(1L, true, true)).orElseThrow();
        assertThat(started.revision()).isEqualTo(1L);
        assertThat(started.phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
        assertThat(started.ticksRemaining()).isEqualTo(2L);
        assertThat(started.cancellationReason()).isEmpty();
        assertThat(coordinator.updateRoster(twoPlayers(1L, true, true))).isEmpty();

        LobbyMatchSnapshot elapsed = coordinator.advanceTick(0L).orElseThrow();
        assertThat(elapsed.revision()).isEqualTo(2L);
        assertThat(elapsed.ticksRemaining()).isOne();

        LobbyMatchSnapshot cancelled =
                coordinator.updateRoster(twoPlayers(2L, true, false)).orElseThrow();
        assertThat(cancelled.revision()).isEqualTo(3L);
        assertThat(cancelled.phase()).isEqualTo(MatchPhase.WAITING_FOR_PLAYERS);
        assertThat(cancelled.ticksRemaining()).isZero();
        assertThat(cancelled.cancellationReason())
                .contains(CountdownCancellationReason.LOBBY_NOT_READY);

        LobbyMatchSnapshot restarted =
                coordinator.updateRoster(twoPlayers(3L, true, true)).orElseThrow();
        assertThat(restarted.revision()).isEqualTo(4L);
        assertThat(restarted.phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
        assertThat(restarted.ticksRemaining()).isEqualTo(2L);
        assertThat(restarted.cancellationReason()).isEmpty();
    }

    @Test
    void entersPreparationExactlyOnceAndStopsAcceptingLobbyCommands() {
        LobbyMatchCoordinator coordinator = new LobbyMatchCoordinator(LOBBY, MATCH);
        coordinator.updateRoster(twoPlayers(1L, true, true));

        LobbyMatchSnapshot lastCountdownTick = coordinator.advanceTick(0L).orElseThrow();
        LobbyMatchSnapshot prepared = coordinator.advanceTick(1L).orElseThrow();

        assertThat(lastCountdownTick.phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
        assertThat(lastCountdownTick.ticksRemaining()).isOne();
        assertThat(prepared.phase()).isEqualTo(MatchPhase.PREPARATION);
        assertThat(prepared.ticksRemaining()).isEqualTo(2L);
        assertThat(coordinator.acceptsLobbyCommands()).isFalse();

        long preparationRevision = prepared.revision();
        assertThat(coordinator.advanceTick(1L)).isEmpty();
        assertThat(coordinator.advanceTick(2L)).isEmpty();
        assertThat(coordinator.snapshot().revision()).isEqualTo(preparationRevision);
        assertThat(coordinator.snapshot().phase()).isEqualTo(MatchPhase.PREPARATION);
        assertThat(coordinator.snapshot().ticksRemaining()).isEqualTo(2L);
    }

    @Test
    void membershipChangesAfterPreparationDoNotRollThePhaseBack() {
        LobbyMatchCoordinator coordinator = new LobbyMatchCoordinator(LOBBY, MATCH);
        coordinator.updateRoster(twoPlayers(1L, true, true));
        coordinator.advanceTick(0L);
        coordinator.advanceTick(1L);

        LobbyMatchSnapshot updated = coordinator.updateRoster(onePlayer(2L)).orElseThrow();

        assertThat(updated.phase()).isEqualTo(MatchPhase.PREPARATION);
        assertThat(updated.connectedPlayers()).isOne();
        assertThat(updated.rosterRevision()).isEqualTo(2L);
        assertThat(coordinator.acceptsLobbyCommands()).isFalse();
    }

    @Test
    void rejectsRosterRevisionGapsAndDifferentStateAtTheSameRevision() {
        LobbyMatchCoordinator coordinator = new LobbyMatchCoordinator(LOBBY, MATCH);

        assertThatThrownBy(() -> coordinator.updateRoster(twoPlayers(2L, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gap");
        assertThat(coordinator.snapshot().revision()).isZero();

        coordinator.updateRoster(twoPlayers(1L, true, true));
        assertThatThrownBy(() -> coordinator.updateRoster(twoPlayers(1L, true, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same roster revision");
        assertThat(coordinator.snapshot().rosterRevision()).isEqualTo(1L);
        assertThat(coordinator.snapshot().phase()).isEqualTo(MatchPhase.START_COUNTDOWN);
    }

    @Test
    void rejectsTickGapsAndMakesDuplicateTicksIdempotent() {
        LobbyMatchCoordinator coordinator = new LobbyMatchCoordinator(LOBBY, MATCH);

        assertThatThrownBy(() -> coordinator.advanceTick(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gap");
        assertThat(coordinator.advanceTick(0L)).isEmpty();
        assertThat(coordinator.advanceTick(0L)).isEmpty();
        assertThatThrownBy(() -> coordinator.advanceTick(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void requiresOneSharedMinimumPlayerPolicy() {
        MatchConfiguration mismatched = new MatchConfiguration(3, 2, 2, 1, 2, 1, 2, 1, 1);

        assertThatThrownBy(() -> new LobbyMatchCoordinator(LOBBY, mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum player counts");
    }

    private static LobbyRosterState twoPlayers(
            long revision, boolean alphaReady, boolean bravoReady) {
        return new LobbyRosterState(
                revision,
                List.of(
                        participant("alpha", TeamId.RED, alphaReady),
                        participant("bravo", TeamId.BLUE, bravoReady)));
    }

    private static LobbyRosterState onePlayer(long revision) {
        return new LobbyRosterState(revision, List.of(participant("alpha", TeamId.RED, false)));
    }

    private static LobbyParticipantState participant(String id, TeamId team, boolean ready) {
        return new LobbyParticipantState(new LobbyParticipantId(id), Optional.of(team), ready);
    }
}
