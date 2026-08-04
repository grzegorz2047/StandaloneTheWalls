package pl.grzegorz2047.standalonethewalls.client.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class ConnectedPreparationSpawnAssignmentTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void acceptsOneAssignmentAfterTheAuthoritativePreparationSnapshot()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment = assignment(2L, 1L, LobbyTeam.GREEN);

        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        waitUntil(() -> prepared.session().currentPreparationSpawnAssignment().isPresent());

        assertThat(prepared.session().currentPreparationSpawnAssignment()).contains(assignment);
        assertThat(prepared.session().terminalFailure()).isEmpty();
        prepared
                .session()
                .closeAsync()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void closesOnMalformedAssignmentPayload()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();

        prepared.lobby().deliverPreparationSpawnPayload(new byte[] {1}, 4L);

        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_MALFORMED);
    }

    @Test
    void closesWhenAssignmentArrivesBeforePreparation()
            throws InterruptedException, ExecutionException, TimeoutException {
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        ConnectedLobbySession session = takeSession(lobby);

        lobby.deliverPreparationSpawnAssignment(assignment(1L, 1L, LobbyTeam.GREEN), 2L);

        assertFailure(
                session, DirectConnectFailureCode.PREPARATION_SPAWN_UNEXPECTED_PHASE);
    }

    @Test
    void closesOnDuplicateAssignment()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();
        PreparationSpawnAssignment assignment = assignment(2L, 1L, LobbyTeam.GREEN);
        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 4L);
        waitUntil(() -> prepared.session().currentPreparationSpawnAssignment().isPresent());

        prepared.lobby().deliverPreparationSpawnAssignment(assignment, 5L);

        assertFailure(prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_DUPLICATE);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherRosterRevision()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();

        prepared
                .lobby()
                .deliverPreparationSpawnAssignment(
                        assignment(3L, 1L, LobbyTeam.GREEN), 4L);

        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_ROSTER_MISMATCH);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherRound()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();

        prepared
                .lobby()
                .deliverPreparationSpawnAssignment(
                        assignment(2L, 2L, LobbyTeam.GREEN), 4L);

        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_ROUND_MISMATCH);
    }

    @Test
    void closesWhenAssignmentTargetsAnotherTeam()
            throws InterruptedException, ExecutionException, TimeoutException {
        PreparedLobby prepared = preparedLobby();

        prepared
                .lobby()
                .deliverPreparationSpawnAssignment(assignment(2L, 1L, LobbyTeam.BLUE), 4L);

        assertFailure(
                prepared.session(), DirectConnectFailureCode.PREPARATION_SPAWN_TEAM_MISMATCH);
    }

    private static PreparedLobby preparedLobby() throws InterruptedException {
        DirectConnectUiTestFixtures.ControlledLobby lobby =
                DirectConnectUiTestFixtures.controlledLobby();
        ConnectedLobbySession session = takeSession(lobby);
        LobbySnapshot roster =
                DirectConnectUiTestFixtures.snapshot(
                        2L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true);
        lobby.deliverSnapshot(roster, 2L);
        waitUntil(() -> session.currentSnapshot().revision() == 2L);

        LobbyMatchPhaseSnapshot preparation =
                new LobbyMatchPhaseSnapshot(
                        2L,
                        2L,
                        10L,
                        LobbyMatchPhase.PREPARATION,
                        100L,
                        roster.members().size(),
                        1L,
                        LobbyCountdownCancellationReason.NONE);
        lobby.deliverMatchSnapshot(preparation, 3L);
        waitUntil(() -> session.currentMatchSnapshot().phase() == LobbyMatchPhase.PREPARATION);
        return new PreparedLobby(lobby, session);
    }

    private static ConnectedLobbySession takeSession(
            DirectConnectUiTestFixtures.ControlledLobby lobby) {
        DirectConnectResult result =
                lobby.connectedResult(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
        return ((DirectConnectResult.Connected) result).takeSession();
    }

    private static PreparationSpawnAssignment assignment(
            long rosterRevision, long roundNumber, LobbyTeam team) {
        return new PreparationSpawnAssignment(
                rosterRevision,
                roundNumber,
                "arena-one",
                digest(),
                team,
                7,
                10.5d,
                2.0d,
                -4.25d,
                90.0d);
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationSpawnAssignment.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }

    private static void assertFailure(
            ConnectedLobbySession session, DirectConnectFailureCode expected)
            throws InterruptedException, ExecutionException, TimeoutException {
        Optional<DirectConnectFailure> failure =
                session.termination()
                        .toCompletableFuture()
                        .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(failure).isPresent();
        assertThat(failure.orElseThrow().code()).isEqualTo(expected);
        assertThat(session.isOpen()).isFalse();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private record PreparedLobby(
            DirectConnectUiTestFixtures.ControlledLobby lobby, ConnectedLobbySession session) {}
}
