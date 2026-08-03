package pl.grzegorz2047.standalonethewalls.client.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;

class ConnectedLobbyMatchSnapshotTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void acceptsExactlyTheNextSynchronizedRevision() throws Exception {
        TestSession test = openSession();
        LobbyMatchPhaseSnapshot next = snapshot(2L, 1L, 2, LobbyMatchPhase.START_COUNTDOWN, 20L);

        test.lobby().deliverMatchSnapshot(next, 2L);
        waitUntil(() -> test.session().currentMatchSnapshot().revision() == 2L);

        assertThat(test.session().currentMatchSnapshot()).isEqualTo(next);
        assertThat(test.session().terminalFailure()).isEmpty();
        test.session().closeAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void closesOnAStaleRevision() throws Exception {
        assertTerminalFailure(
                snapshot(0L, 1L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_STALE);
    }

    @Test
    void closesOnADuplicateRevision() throws Exception {
        assertTerminalFailure(
                snapshot(1L, 1L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_DUPLICATE);
    }

    @Test
    void closesOnARevisionGap() throws Exception {
        assertTerminalFailure(
                snapshot(3L, 1L, 2, LobbyMatchPhase.START_COUNTDOWN, 20L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_REVISION_GAP);
    }

    @Test
    void closesWhenTheMatchSnapshotDescribesAnotherRosterRevision() throws Exception {
        assertTerminalFailure(
                snapshot(2L, 2L, 2, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH);
    }

    @Test
    void closesWhenTheMatchSnapshotDescribesAnotherPlayerCount() throws Exception {
        assertTerminalFailure(
                snapshot(2L, 1L, 1, LobbyMatchPhase.WAITING_FOR_PLAYERS, 0L),
                DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH);
    }

    private static void assertTerminalFailure(
            LobbyMatchPhaseSnapshot snapshot, DirectConnectFailureCode expected) throws Exception {
        TestSession test = openSession();

        test.lobby().deliverMatchSnapshot(snapshot, 2L);
        Optional<DirectConnectFailure> failure =
                test.session()
                        .termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(failure).isPresent();
        assertThat(failure.orElseThrow().code()).isEqualTo(expected);
        assertThat(test.session().isOpen()).isFalse();
    }

    private static TestSession openSession() {
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        DirectConnectResult result =
                lobby.connectedResult(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
        ConnectedLobbySession session = ((DirectConnectResult.Connected) result).takeSession();
        return new TestSession(lobby, session);
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

    private record TestSession(
            DirectConnectUiTestFixtures.ControlledLobby lobby, ConnectedLobbySession session) {}
}
