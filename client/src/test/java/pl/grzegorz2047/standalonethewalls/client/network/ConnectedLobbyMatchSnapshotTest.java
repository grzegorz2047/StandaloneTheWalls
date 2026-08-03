package pl.grzegorz2047.standalonethewalls.client.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;

class ConnectedLobbyMatchSnapshotTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void acceptsExactlyTheNextSynchronizedRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        DirectConnectUiTestFixtures.ControlledLobby lobby = openLobby();
        ConnectedLobbySession session = takeSession(lobby);
        LobbyMatchPhaseSnapshot next = snapshot(2L, 1L, 2, LobbyMatchPhase.START_COUNTDOWN, 20L);

        lobby.deliverMatchSnapshot(next, 2L);
        waitUntil(() -> session.currentMatchSnapshot().revision() == 2L);

        assertThat(session.currentMatchSnapshot()).isEqualTo(next);
        assertThat(session.terminalFailure()).isEmpty();
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void closesOnAStaleRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertTerminalFailure(
                snapshot(0L, 1L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_STALE);
    }

    @Test
    void closesOnADuplicateRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertTerminalFailure(
                snapshot(1L, 1L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_DUPLICATE);
    }

    @Test
    void closesOnARevisionGap()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertTerminalFailure(
                snapshot(3L, 1L, 2, LobbyMatchPhase.START_COUNTDOWN, 20L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_REVISION_GAP);
    }

    @Test
    void closesWhenTheMatchSnapshotDescribesAnotherRosterRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertTerminalFailure(
                snapshot(2L, 2L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH);
    }

    @Test
    void closesWhenTheMatchSnapshotDescribesAnotherPlayerCount()
            throws InterruptedException, ExecutionException, TimeoutException {
        assertTerminalFailure(
                snapshot(2L, 1L, 1, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH);
    }

    private static void assertTerminalFailure(
            LobbyMatchPhaseSnapshot snapshot, DirectConnectFailureCode expected)
            throws InterruptedException, ExecutionException, TimeoutException {
        DirectConnectUiTestFixtures.ControlledLobby lobby = openLobby();
        ConnectedLobbySession session = takeSession(lobby);

        lobby.deliverMatchSnapshot(snapshot, 2L);
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(failure).isPresent();
        assertThat(failure.orElseThrow().code()).isEqualTo(expected);
        assertThat(session.isOpen()).isFalse();
    }

    private static DirectConnectUiTestFixtures.ControlledLobby openLobby() {
        return DirectConnectUiTestFixtures.controlledLobby();
    }

    private static ConnectedLobbySession takeSession(
            DirectConnectUiTestFixtures.ControlledLobby lobby) {
        DirectConnectResult result =
                lobby.connectedResult(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
        return ((DirectConnectResult.Connected) result).takeSession();
    }

    private static LobbyMatchPhaseSnapshot snapshot(
            long revision,
            long rosterRevision,
            int connectedPlayers,
            LobbyMatchPhase phase,
            long ticksRemaining) {
        return new LobbyMatchPhaseSnapshot(
                revision,
                rosterRevision,
                5L,
                phase,
                ticksRemaining,
                connectedPlayers,
                1L,
                LobbyCountdownCancellationReason.NONE);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
