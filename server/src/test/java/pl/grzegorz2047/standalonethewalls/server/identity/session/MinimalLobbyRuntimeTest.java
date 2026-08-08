package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyConfiguration;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchConfiguration;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableChannel;
import pl.grzegorz2047.standalonethewalls.protocol.ReliableSendResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.SecureChannelBinding;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySelectTeamCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySetReadyCommand;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationMovementProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.ClientRealtimeTicket;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketRejection;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketRequest;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketResult;
import pl.grzegorz2047.standalonethewalls.protocol.realtime.RealtimeTicketResultStatus;
import pl.grzegorz2047.standalonethewalls.server.lobby.MinimalLobbyRuntime;
import pl.grzegorz2047.standalonethewalls.server.realtime.RealtimeTicketProvisioner;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.OneTimeRealtimeTicketStore;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketEntropy;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketIdentity;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketRedemption;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RealtimeTicketStoreException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.realtime.RedeemedRealtimeTicket;

class MinimalLobbyRuntimeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ServerId SERVER_ID = new ServerId("sfs1_" + "z".repeat(52));

    @Test
    void transfersOneAuthorizedSessionAndReleasesCapacityAfterEof()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, transport);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> transport.channel.sent().size() >= 2);

            SentMessage joinedMessage = transport.channel.sent().get(0);
            SentMessage snapshotMessage = transport.channel.sent().get(1);
            assertThat(joinedMessage.messageType()).isEqualTo(MessageType.LOBBY_JOINED);
            assertThat(snapshotMessage.messageType()).isEqualTo(MessageType.LOBBY_SNAPSHOT);
            LobbyJoined joined = LobbyProtocolCodec.decodeJoined(joinedMessage.payload());
            LobbySnapshot snapshot = LobbyProtocolCodec.decodeSnapshot(snapshotMessage.payload());
            assertThat(joined.revision()).isEqualTo(1L);
            assertThat(joined.self().playerId()).isEqualTo(transport.playerId());
            assertThat(snapshot.revision()).isEqualTo(1L);
            assertThat(snapshot.members()).containsExactly(joined.self());
            assertThat(snapshot.members().getFirst().team()).isEqualTo(LobbyTeam.UNASSIGNED);
            assertThat(snapshot.members().getFirst().ready()).isFalse();
            assertThat(lobby.memberCount()).isEqualTo(1);
            assertThat(queue.activeTransferCount()).isEqualTo(1);

            transport.channel.completeEof();
            waitUntil(() -> lobby.memberCount() == 0 && queue.activeTransferCount() == 0);

            assertThat(transport.closeCount()).isEqualTo(1);
            assertThat(lobby.revision()).isEqualTo(2L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void broadcastsStrictlySortedCompleteSnapshotsToTwoMembers()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession bravo = new TestSession(1, playerId('b'), "bravo");
        TestSession alpha = new TestSession(2, playerId('a'), "alpha");
        enqueue(queue, bravo);
        enqueue(queue, alpha);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(
                    () ->
                            latestSnapshotMessage(bravo).isPresent()
                                    && latestSnapshotMessage(alpha).isPresent()
                                    && lobby.memberCount() == 2);

            LobbySnapshot bravoSnapshot = latestSnapshot(bravo);
            LobbySnapshot alphaSnapshot = latestSnapshot(alpha);

            assertThat(bravoSnapshot).isEqualTo(alphaSnapshot);
            assertThat(bravoSnapshot.revision()).isEqualTo(2L);
            assertThat(bravoSnapshot.members())
                    .extracting(LobbyMember::playerId)
                    .containsExactly(alpha.playerId(), bravo.playerId());
            assertThat(bravoSnapshot.members())
                    .allMatch(member -> member.team() == LobbyTeam.UNASSIGNED && !member.ready());
            assertThat(queue.activeTransferCount()).isEqualTo(2);
        } finally {
            lobby.close();
            queue.close();
        }
        assertThat(bravo.closeCount()).isEqualTo(1);
        assertThat(alpha.closeCount()).isEqualTo(1);
        assertThat(queue.activeTransferCount()).isZero();
    }

    @Test
    void appliesTeamAndReadyCommandsAndBroadcastsAuthoritativeRoster()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);

            sendSelect(alpha, 1L, LobbyTeam.GREEN);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 3L, LobbyCommandOutcome.APPLIED);

            sendSelect(bravo, 1L, LobbyTeam.BLUE);
            waitForResult(bravo, 1L);
            assertResult(bravo, 1L, 4L, LobbyCommandOutcome.APPLIED);

            sendReady(alpha, 2L, true);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 5L, LobbyCommandOutcome.APPLIED);

            sendReady(bravo, 2L, true);
            waitForResult(bravo, 2L);
            assertResult(bravo, 2L, 6L, LobbyCommandOutcome.APPLIED);
            waitUntil(
                    () ->
                            latestSnapshotUnchecked(alpha).revision() == 6L
                                    && latestSnapshotUnchecked(bravo).revision() == 6L);

            LobbySnapshot snapshot = latestSnapshot(alpha);
            assertThat(snapshot).isEqualTo(latestSnapshot(bravo));
            assertThat(snapshot.members())
                    .containsExactly(
                            new LobbyMember(
                                    alpha.playerId(), alpha.handle(), LobbyTeam.GREEN, true),
                            new LobbyMember(
                                    bravo.playerId(), bravo.handle(), LobbyTeam.BLUE, true));
            assertThat(lobby.revision()).isEqualTo(6L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void reportsRejectedAndIdempotentCommandsWithoutFalseRevisionOrBroadcast()
            throws InterruptedException, LobbyProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, alpha);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && snapshotCount(alpha) >= 1
                                    && latestSnapshotUnchecked(alpha).revision() == 1L);
            int initialSnapshots = snapshotCount(alpha);

            sendReady(alpha, 1L, true);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 1L, LobbyCommandOutcome.TEAM_REQUIRED);
            assertThat(snapshotCount(alpha)).isEqualTo(initialSnapshots);

            sendReady(alpha, 2L, false);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 1L, LobbyCommandOutcome.NO_CHANGE);
            assertThat(snapshotCount(alpha)).isEqualTo(initialSnapshots);

            sendSelect(alpha, 3L, LobbyTeam.GREEN);
            waitForResult(alpha, 3L);
            assertResult(alpha, 3L, 2L, LobbyCommandOutcome.APPLIED);
            waitUntil(() -> latestSnapshotUnchecked(alpha).revision() == 2L);
            int afterApplied = snapshotCount(alpha);

            sendSelect(alpha, 4L, LobbyTeam.GREEN);
            waitForResult(alpha, 4L);
            assertResult(alpha, 4L, 2L, LobbyCommandOutcome.NO_CHANGE);
            assertThat(snapshotCount(alpha)).isEqualTo(afterApplied);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void mapsDisabledFullAndImbalancedTeamsWithoutMutatingTheRoster() throws InterruptedException {
        LobbyConfiguration configuration =
                new LobbyConfiguration(EnumSet.of(TeamId.GREEN, TeamId.BLUE), 2, 1, 2);
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue, configuration);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);

            sendSelect(alpha, 1L, LobbyTeam.RED);
            waitForResult(alpha, 1L);
            assertResult(alpha, 1L, 2L, LobbyCommandOutcome.TEAM_DISABLED);

            sendSelect(alpha, 2L, LobbyTeam.GREEN);
            waitForResult(alpha, 2L);
            assertResult(alpha, 2L, 3L, LobbyCommandOutcome.APPLIED);

            sendSelect(bravo, 1L, LobbyTeam.GREEN);
            waitForResult(bravo, 1L);
            assertResult(bravo, 1L, 3L, LobbyCommandOutcome.TEAM_FULL);

            sendSelect(bravo, 2L, LobbyTeam.BLUE);
            waitForResult(bravo, 2L);
            assertResult(bravo, 2L, 4L, LobbyCommandOutcome.APPLIED);

            sendSelect(alpha, 3L, LobbyTeam.BLUE);
            waitForResult(alpha, 3L);
            assertResult(alpha, 3L, 4L, LobbyCommandOutcome.TEAM_FULL);
            assertThat(lobby.revision()).isEqualTo(4L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void replayedRequestIdFailsClosedAndRemovesOnlyTheOffendingSession()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            sendSelect(alpha, 1L, LobbyTeam.GREEN);
            waitForResult(alpha, 1L);

            sendReady(alpha, 1L, false);
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && alpha.closeCount() == 1
                                    && latestSnapshotUnchecked(bravo).revision() == 4L);

            assertThat(bravo.closeCount()).isZero();
            assertThat(latestSnapshotUnchecked(bravo).members())
                    .extracting(LobbyMember::playerId)
                    .containsExactly(bravo.playerId());
            assertThat(lobby.revision()).isEqualTo(4L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void rejectsASecondActiveSessionForTheSamePlayerId() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        PlayerId sharedPlayerId = playerId('a');
        TestSession first = new TestSession(1, sharedPlayerId, "alpha");
        TestSession duplicate = new TestSession(2, sharedPlayerId, "alpha");
        enqueue(queue, first);
        enqueue(queue, duplicate);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1 && duplicate.closeCount() == 1);

            assertThat(first.closeCount()).isZero();
            assertThat(queue.activeTransferCount()).isEqualTo(1);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void unexpectedClientMessageFailsClosed() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(1, playerId('a'), "alpha");
        enqueue(queue, transport);
        MinimalLobbyRuntime lobby = lobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);
            transport.channel.completeMessage(envelope(transport, MessageType.PING, new byte[0]));

            waitUntil(() -> lobby.memberCount() == 0 && queue.activeTransferCount() == 0);

            assertThat(transport.closeCount()).isEqualTo(1);
            assertThat(lobby.revision()).isEqualTo(2L);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void synchronizesCountdownCancellationRestartAndPreparationAcrossTwoSessions()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.START_COUNTDOWN
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 3L
                                    && latestMatchSnapshotUnchecked(alpha)
                                            .equals(latestMatchSnapshotUnchecked(bravo)));

            LobbyMatchPhaseSnapshot started = latestMatchSnapshotUnchecked(alpha);
            assertThat(started.rosterRevision()).isEqualTo(6L);
            assertThat(started.connectedPlayers()).isEqualTo(2);
            assertThat(started.cancellationReason())
                    .isEqualTo(LobbyCountdownCancellationReason.NONE);

            assertThat(lobby.offerSimulationTick(0L)).isTrue();
            waitUntil(() -> latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 2L);
            assertThat(latestMatchSnapshotUnchecked(alpha))
                    .isEqualTo(latestMatchSnapshotUnchecked(bravo));

            assertThat(lobby.offerSimulationTick(1L)).isTrue();
            waitUntil(() -> latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L);

            sendReady(bravo, 3L, false);
            waitForResult(bravo, 3L);
            assertResult(bravo, 3L, 7L, LobbyCommandOutcome.APPLIED);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.WAITING_FOR_PLAYERS
                                    && latestMatchSnapshotUnchecked(alpha).cancellationReason()
                                            == LobbyCountdownCancellationReason.LOBBY_NOT_READY);
            LobbyMatchPhaseSnapshot cancelled = latestMatchSnapshotUnchecked(alpha);
            assertThat(cancelled).isEqualTo(latestMatchSnapshotUnchecked(bravo));
            assertThat(cancelled.rosterRevision()).isEqualTo(7L);

            assertThat(lobby.offerSimulationTick(2L)).isTrue();
            sendReady(bravo, 4L, true);
            waitForResult(bravo, 4L);
            assertResult(bravo, 4L, 8L, LobbyCommandOutcome.APPLIED);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.START_COUNTDOWN
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 3L
                                    && latestMatchSnapshotUnchecked(alpha).revision()
                                            > cancelled.revision());
            LobbyMatchPhaseSnapshot restarted = latestMatchSnapshotUnchecked(alpha);
            assertThat(restarted.authoritativeTick()).isEqualTo(2L);
            assertThat(restarted).isEqualTo(latestMatchSnapshotUnchecked(bravo));

            assertThat(lobby.offerSimulationTick(3L)).isTrue();
            assertThat(lobby.offerSimulationTick(4L)).isTrue();
            assertThat(lobby.offerSimulationTick(5L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.PREPARATION
                                    && latestMatchSnapshotUnchecked(alpha)
                                            .equals(latestMatchSnapshotUnchecked(bravo)));
            LobbyMatchPhaseSnapshot preparation = latestMatchSnapshotUnchecked(alpha);
            assertThat(preparation).isEqualTo(latestMatchSnapshotUnchecked(bravo));
            assertThat(preparation.authoritativeTick()).isEqualTo(5L);
            assertThat(matchSnapshotCount(alpha, LobbyMatchPhase.PREPARATION)).isOne();
            assertThat(matchSnapshotCount(bravo, LobbyMatchPhase.PREPARATION)).isOne();
            waitUntil(
                    () ->
                            preparationAssignmentCount(alpha) == 1
                                    && preparationAssignmentCount(bravo) == 1);
            PreparationSpawnAssignment alphaAssignment =
                    latestPreparationAssignmentUnchecked(alpha);
            PreparationSpawnAssignment bravoAssignment =
                    latestPreparationAssignmentUnchecked(bravo);
            assertThat(alphaAssignment.mapId()).isEqualTo(MinimalPreparationBundle.MAP_ID);
            assertThat(bravoAssignment.mapId()).isEqualTo(MinimalPreparationBundle.MAP_ID);
            assertThat(alphaAssignment.mapSha256())
                    .containsExactly(bravoAssignment.mapSha256())
                    .hasSize(32);
            assertThat(alphaAssignment.rosterRevision()).isEqualTo(8L);
            assertThat(bravoAssignment.rosterRevision()).isEqualTo(8L);
            assertThat(alphaAssignment.team()).isEqualTo(LobbyTeam.GREEN);
            assertThat(bravoAssignment.team()).isEqualTo(LobbyTeam.BLUE);
            assertThat(alphaAssignment.spawnIndex()).isNotEqualTo(bravoAssignment.spawnIndex());
            assertThat(preparationSnapshotMessageIndex(alpha))
                    .isLessThan(preparationAssignmentMessageIndex(alpha));
            assertThat(preparationSnapshotMessageIndex(bravo))
                    .isLessThan(preparationAssignmentMessageIndex(bravo));
            waitUntil(
                    () ->
                            preparationWorldSnapshotCount(alpha) == 1
                                    && preparationWorldSnapshotCount(bravo) == 1);
            PreparationWorldSnapshot initialWorld = latestPreparationWorldSnapshotUnchecked(alpha);
            assertThat(initialWorld).isEqualTo(latestPreparationWorldSnapshotUnchecked(bravo));
            assertThat(initialWorld.authoritativeTick()).isEqualTo(5L);
            assertThat(initialWorld.players())
                    .extracting(PreparationPlayerSnapshot::playerId)
                    .containsExactly(alpha.playerId(), bravo.playerId());
            assertThat(preparationAssignmentMessageIndex(alpha))
                    .isLessThan(preparationWorldSnapshotMessageIndex(alpha));
            assertThat(preparationAssignmentMessageIndex(bravo))
                    .isLessThan(preparationWorldSnapshotMessageIndex(bravo));

            assertThat(lobby.offerSimulationTick(6L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).authoritativeTick() == 6L
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.PREPARATION
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L
                                    && latestMatchSnapshotUnchecked(alpha)
                                            .equals(latestMatchSnapshotUnchecked(bravo)));
            LobbyMatchPhaseSnapshot lastPreparation = latestMatchSnapshotUnchecked(alpha);
            assertThat(lastPreparation.revision()).isEqualTo(preparation.revision() + 1L);

            assertThat(lobby.offerSimulationTick(7L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).authoritativeTick() == 7L
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.WALLS_OPENING
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L
                                    && latestMatchSnapshotUnchecked(alpha)
                                            .equals(latestMatchSnapshotUnchecked(bravo)));
            LobbyMatchPhaseSnapshot opening = latestMatchSnapshotUnchecked(alpha);
            assertThat(opening.revision()).isEqualTo(lastPreparation.revision() + 1L);

            assertThat(lobby.offerSimulationTick(8L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).authoritativeTick() == 8L
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.OPEN_COMBAT
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 2L
                                    && latestMatchSnapshotUnchecked(alpha)
                                            .equals(latestMatchSnapshotUnchecked(bravo)));
            LobbyMatchPhaseSnapshot openCombat = latestMatchSnapshotUnchecked(alpha);
            assertThat(openCombat.revision()).isEqualTo(opening.revision() + 1L);

            int rosterSnapshotsBeforeLockedCommand = snapshotCount(alpha);
            sendSelect(alpha, 3L, LobbyTeam.RED);
            waitForResult(alpha, 3L);
            assertResult(alpha, 3L, 8L, LobbyCommandOutcome.MATCH_ALREADY_STARTED);
            assertThat(snapshotCount(alpha)).isEqualTo(rosterSnapshotsBeforeLockedCommand);
            assertThat(latestSnapshotUnchecked(alpha).revision()).isEqualTo(8L);

            PreparationPlayerSnapshot initialAlpha = player(initialWorld, alpha.playerId());
            PreparationPlayerSnapshot initialBravo = player(initialWorld, bravo.playerId());
            sendPreparationInput(
                    alpha,
                    new PreparationInput(
                            preparation.roundNumber(),
                            1L,
                            127,
                            0,
                            false,
                            yawCentidegrees(alphaAssignment.yawDegrees()),
                            0));

            PreparationWorldSnapshot moved = null;
            long movementTick = 9L;
            while (movementTick <= 15L && moved == null) {
                assertThat(lobby.offerSimulationTick(movementTick)).isTrue();
                if (movementTick % 2L == 0L) {
                    long expectedTick = movementTick;
                    waitUntil(
                            () ->
                                    latestPreparationWorldSnapshotUnchecked(alpha)
                                                    .authoritativeTick()
                                            >= expectedTick);
                    PreparationWorldSnapshot candidate =
                            latestPreparationWorldSnapshotUnchecked(alpha);
                    if (player(candidate, alpha.playerId()).lastProcessedInputSequence() == 1L) {
                        moved = candidate;
                    }
                }
                movementTick++;
            }
            assertThat(moved).isNotNull();
            PreparationWorldSnapshot authoritativeMovement = moved;
            assertThat(authoritativeMovement)
                    .isEqualTo(latestPreparationWorldSnapshotUnchecked(bravo));
            PreparationPlayerSnapshot movedAlpha = player(authoritativeMovement, alpha.playerId());
            PreparationPlayerSnapshot unmovedBravo = player(authoritativeMovement, bravo.playerId());
            assertThat(movedAlpha.lastProcessedInputSequence()).isEqualTo(1L);
            assertThat(
                            Math.hypot(
                                    movedAlpha.xMillimetres() - initialAlpha.xMillimetres(),
                                    movedAlpha.zMillimetres() - initialAlpha.zMillimetres()))
                    .isGreaterThan(0.0d)
                    .isLessThanOrEqualTo(1_750.0d);
            assertThat(unmovedBravo).isEqualTo(initialBravo);

            LobbyMatchPhaseSnapshot beforeDisconnect = latestMatchSnapshotUnchecked(alpha);
            bravo.channel.completeEof();
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && bravo.closeCount() == 1
                                    && latestMatchSnapshotUnchecked(alpha).connectedPlayers() == 1);
            LobbyMatchPhaseSnapshot afterDisconnect = latestMatchSnapshotUnchecked(alpha);
            assertThat(afterDisconnect.phase()).isEqualTo(beforeDisconnect.phase());
            assertThat(afterDisconnect.ticksRemaining())
                    .isEqualTo(beforeDisconnect.ticksRemaining());
            assertThat(afterDisconnect.authoritativeTick())
                    .isGreaterThanOrEqualTo(beforeDisconnect.authoritativeTick());
            assertThat(afterDisconnect.revision()).isEqualTo(beforeDisconnect.revision() + 1L);

            long removalSnapshotTick = authoritativeMovement.authoritativeTick() + 2L;
            for (long tick = authoritativeMovement.authoritativeTick() + 1L;
                    tick <= removalSnapshotTick;
                    tick++) {
                assertThat(lobby.offerSimulationTick(tick)).isTrue();
            }
            waitUntil(
                    () ->
                            latestPreparationWorldSnapshotUnchecked(alpha).authoritativeTick()
                                    >= removalSnapshotTick);
            assertThat(latestPreparationWorldSnapshotUnchecked(alpha).players())
                    .extracting(PreparationPlayerSnapshot::playerId)
                    .containsExactly(alpha.playerId());
            assertThat(preparationAssignmentCount(alpha)).isOne();
            assertThat(preparationAssignmentCount(bravo)).isOne();
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void joinDuringCountdownReceivesTheFullAuthoritativeCancellationState()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(3);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        TestSession charlie = new TestSession(3, playerId('c'), "charlie");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);

            enqueue(queue, charlie);
            waitUntil(
                    () ->
                            lobby.memberCount() == 3
                                    && latestMatchSnapshotMessage(charlie).isPresent());

            LobbyMatchPhaseSnapshot alphaSnapshot = latestMatchSnapshotUnchecked(alpha);
            LobbyMatchPhaseSnapshot bravoSnapshot = latestMatchSnapshotUnchecked(bravo);
            LobbyMatchPhaseSnapshot charlieSnapshot = latestMatchSnapshotUnchecked(charlie);
            assertThat(alphaSnapshot).isEqualTo(bravoSnapshot).isEqualTo(charlieSnapshot);
            assertThat(charlieSnapshot.phase()).isEqualTo(LobbyMatchPhase.WAITING_FOR_PLAYERS);
            assertThat(charlieSnapshot.cancellationReason())
                    .isEqualTo(LobbyCountdownCancellationReason.LOBBY_NOT_READY);
            assertThat(charlieSnapshot.rosterRevision()).isEqualTo(7L);
            assertThat(charlieSnapshot.connectedPlayers()).isEqualTo(3);
            assertThat(latestSnapshotUnchecked(charlie).members()).hasSize(3);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void preparationSpawnSendFailureRemovesOnlyBrokenSessionAndKeepsRemainingWorld()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);
            assertThat(lobby.offerSimulationTick(0L)).isTrue();
            waitUntil(() -> latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 2L);
            assertThat(lobby.offerSimulationTick(1L)).isTrue();
            waitUntil(() -> latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L);

            bravo.channel.failNextSend(MessageType.PREPARATION_SPAWN_ASSIGNMENT);
            assertThat(lobby.offerSimulationTick(2L)).isTrue();
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && bravo.closeCount() == 1
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.PREPARATION
                                    && latestMatchSnapshotUnchecked(alpha).connectedPlayers() == 1
                                    && preparationAssignmentCount(alpha) == 1
                                    && preparationWorldSnapshotCount(alpha) >= 1);

            assertThat(lobby.isRunning()).isTrue();
            assertThat(lobby.failure()).isEmpty();
            assertThat(alpha.closeCount()).isZero();
            assertThat(latestSnapshotUnchecked(alpha).members())
                    .extracting(LobbyMember::playerId)
                    .containsExactly(alpha.playerId());
            assertThat(latestPreparationWorldSnapshotUnchecked(alpha).players())
                    .extracting(PreparationPlayerSnapshot::playerId)
                    .containsExactly(alpha.playerId());
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void openingSnapshotSendFailureRemovesOnlyBrokenSessionAndPreservesOpening()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);
            assertThat(lobby.offerSimulationTick(0L)).isTrue();
            assertThat(lobby.offerSimulationTick(1L)).isTrue();
            assertThat(lobby.offerSimulationTick(2L)).isTrue();
            waitUntil(() -> preparationAssignmentCount(alpha) == 1);
            assertThat(lobby.offerSimulationTick(3L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.PREPARATION
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L);

            bravo.channel.failNextSend(MessageType.LOBBY_MATCH_SNAPSHOT);
            assertThat(lobby.offerSimulationTick(4L)).isTrue();
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && bravo.closeCount() == 1
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.WALLS_OPENING
                                    && latestMatchSnapshotUnchecked(alpha).connectedPlayers() == 1);

            assertThat(lobby.isRunning()).isTrue();
            assertThat(lobby.failure()).isEmpty();
            assertThat(alpha.closeCount()).isZero();
            assertThat(lobby.offerSimulationTick(5L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.OPEN_COMBAT);
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void openCombatSnapshotSendFailureRemovesOnlyBrokenSessionAndPreservesOpenCombat()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);
            for (long tick = 0L; tick <= 4L; tick++) {
                assertThat(lobby.offerSimulationTick(tick)).isTrue();
            }
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.WALLS_OPENING);

            bravo.channel.failNextSend(MessageType.LOBBY_MATCH_SNAPSHOT);
            assertThat(lobby.offerSimulationTick(5L)).isTrue();
            waitUntil(
                    () ->
                            lobby.memberCount() == 1
                                    && bravo.closeCount() == 1
                                    && latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.OPEN_COMBAT
                                    && latestMatchSnapshotUnchecked(alpha).connectedPlayers() == 1);

            assertThat(lobby.isRunning()).isTrue();
            assertThat(lobby.failure()).isEmpty();
            assertThat(alpha.closeCount()).isZero();
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void shutdownAtOpeningBoundaryClosesSessionsAndRejectsOpenCombatTick()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);
            for (long tick = 0L; tick <= 4L; tick++) {
                assertThat(lobby.offerSimulationTick(tick)).isTrue();
            }
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.WALLS_OPENING);

            lobby.close();
            lobby.close();

            assertThat(lobby.isRunning()).isFalse();
            assertThat(lobby.offerSimulationTick(5L)).isFalse();
            assertThat(lobby.failure()).isEmpty();
            assertThat(lobby.memberCount()).isZero();
            assertThat(alpha.closeCount()).isEqualTo(1);
            assertThat(bravo.closeCount()).isEqualTo(1);
            assertThat(queue.activeTransferCount()).isZero();
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void shutdownDuringCountdownClosesEverySessionAndRejectsLaterTicks()
            throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession alpha = new TestSession(1, playerId('a'), "alpha");
        TestSession bravo = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, alpha);
        enqueue(queue, bravo);
        MinimalLobbyRuntime lobby = countdownLobby(queue);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 2);
            readyTwoPlayers(alpha, bravo);
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.START_COUNTDOWN);
            assertThat(lobby.offerSimulationTick(0L)).isTrue();
            waitUntil(() -> latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 2L);

            lobby.close();
            lobby.close();

            assertThat(lobby.isRunning()).isFalse();
            assertThat(lobby.offerSimulationTick(1L)).isFalse();
            assertThat(lobby.failure()).isEmpty();
            assertThat(alpha.closeCount()).isEqualTo(1);
            assertThat(bravo.closeCount()).isEqualTo(1);
            assertThat(queue.activeTransferCount()).isZero();
        } finally {
            lobby.close();
            queue.close();
        }
    }

    @Test
    void runtimeShutdownClosesOwnedSessionsAndReturnsEverySlot() throws InterruptedException {
        AuthorizedPlayerSessionQueue queue = queue(2);
        TestSession first = new TestSession(1, playerId('a'), "alpha");
        TestSession second = new TestSession(2, playerId('b'), "bravo");
        enqueue(queue, first);
        enqueue(queue, second);
        MinimalLobbyRuntime lobby = lobby(queue);
        lobby.start();
        waitUntil(() -> lobby.memberCount() == 2);

        lobby.close();
        lobby.close();

        assertThat(lobby.isRunning()).isFalse();
        assertThat(lobby.memberCount()).isZero();
        assertThat(lobby.revision()).isEqualTo(4L);
        assertThat(first.closeCount()).isEqualTo(1);
        assertThat(second.closeCount()).isEqualTo(1);
        assertThat(queue.activeTransferCount()).isZero();
        queue.close();
    }

    @Test
    void rejectsProductionTicketRequestsWhenDtls13ProviderCapabilityIsUnavailable()
            throws InterruptedException, RealtimeTicketProtocolException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(30, playerId('q'), "provider_gate");
        enqueue(queue, transport);
        RealtimeTicketProvisioner provisioner =
                RealtimeTicketProvisioner.createProduction(1, Duration.ofSeconds(20));
        MinimalLobbyRuntime lobby = realtimeLobby(queue, provisioner);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);

            sendRealtimeTicketRequest(transport, 1L, RealtimeTicketProvisioner.PROFILE_VERSION);
            waitUntil(() -> realtimeTicketMessages(transport).size() == 1);

            try (RealtimeTicketResult result =
                    RealtimeTicketProtocolCodec.decodeResult(
                            realtimeTicketMessages(transport).getFirst().payload())) {
                assertThat(result.status()).isEqualTo(RealtimeTicketResultStatus.REJECTED);
                assertThat(result.rejection())
                        .contains(RealtimeTicketRejection.TEMPORARILY_UNAVAILABLE);
            }
        } finally {
            lobby.close();
            provisioner.close();
            queue.close();
        }
    }

    @Test
    void provisionsOneTicketFromTrustedSessionContextAndRejectsASecondInTheRound()
            throws InterruptedException,
                    RealtimeTicketProtocolException,
                    RealtimeTicketStoreException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(31, playerId('r'), "realtime");
        enqueue(queue, transport);
        QueueEntropy entropy = new QueueEntropy(filled(16, 31), filled(32, 32));
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(Instant.parse("2026-08-05T06:30:00Z"), ZoneOffset.UTC),
                        entropy,
                        new RealtimeTicketStoreConfig(1, Duration.ofSeconds(30)));
        RealtimeTicketProvisioner provisioner =
                new RealtimeTicketProvisioner(store, Duration.ofSeconds(20));
        MinimalLobbyRuntime lobby = realtimeLobby(queue, provisioner);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);

            sendRealtimeTicketRequest(transport, 1L, RealtimeTicketProvisioner.PROFILE_VERSION);
            waitUntil(() -> realtimeTicketMessages(transport).size() == 1);

            try (RealtimeTicketResult result =
                    RealtimeTicketProtocolCodec.decodeResult(
                            realtimeTicketMessages(transport).getFirst().payload())) {
                assertThat(result.status()).isEqualTo(RealtimeTicketResultStatus.ISSUED);
                ClientRealtimeTicket clientTicket = result.ticket().orElseThrow();
                RealtimeTicketRedemption redemption =
                        store.redeem(new RealtimeTicketIdentity(clientTicket.copyIdentity()));
                assertThat(redemption.status()).isEqualTo(RealtimeTicketRedemption.Status.REDEEMED);
                try (RedeemedRealtimeTicket redeemed = redemption.ticket().orElseThrow()) {
                    assertThat(redeemed.context().serverId()).isEqualTo(SERVER_ID);
                    assertThat(redeemed.context().reliableSessionId())
                            .isEqualTo(transport.sessionId());
                    assertThat(redeemed.context().playerId()).isEqualTo(transport.playerId());
                    assertThat(redeemed.context().roundEpoch()).isEqualTo(1L);
                    assertThat(redeemed.preSharedKey().copyBytes()).containsOnly(32);
                }
                assertThat(clientTicket.copyPreSharedKey()).containsOnly(32);
            }

            sendRealtimeTicketRequest(transport, 2L, RealtimeTicketProvisioner.PROFILE_VERSION);
            waitUntil(() -> realtimeTicketMessages(transport).size() == 2);
            try (RealtimeTicketResult rejected =
                    RealtimeTicketProtocolCodec.decodeResult(
                            realtimeTicketMessages(transport).get(1).payload())) {
                assertThat(rejected.status()).isEqualTo(RealtimeTicketResultStatus.REJECTED);
                assertThat(rejected.rejection())
                        .contains(RealtimeTicketRejection.ALREADY_ISSUED_FOR_ROUND);
            }
        } finally {
            lobby.close();
            provisioner.close();
            queue.close();
        }
    }

    @Test
    void failedTicketResultSendRevokesTheUndeliveredCredential()
            throws InterruptedException, RealtimeTicketStoreException {
        AuthorizedPlayerSessionQueue queue = queue(1);
        TestSession transport = new TestSession(32, playerId('s'), "send_failure");
        enqueue(queue, transport);
        OneTimeRealtimeTicketStore store =
                new OneTimeRealtimeTicketStore(
                        Clock.fixed(Instant.parse("2026-08-05T06:45:00Z"), ZoneOffset.UTC),
                        new QueueEntropy(filled(16, 41), filled(32, 42)),
                        new RealtimeTicketStoreConfig(1, Duration.ofSeconds(30)));
        RealtimeTicketProvisioner provisioner =
                new RealtimeTicketProvisioner(store, Duration.ofSeconds(20));
        MinimalLobbyRuntime lobby = realtimeLobby(queue, provisioner);

        try {
            lobby.start();
            waitUntil(() -> lobby.memberCount() == 1);
            transport.channel.failNextSend(MessageType.REALTIME_TICKET_RESULT);

            sendRealtimeTicketRequest(transport, 1L, RealtimeTicketProvisioner.PROFILE_VERSION);
            waitUntil(() -> lobby.memberCount() == 0 && transport.closeCount() == 1);

            assertThat(store.activeTicketCount()).isZero();
        } finally {
            lobby.close();
            provisioner.close();
            queue.close();
        }
    }

    private static MinimalLobbyRuntime lobby(AuthorizedPlayerSessionQueue queue) {
        return new MinimalLobbyRuntime(
                queue, Duration.ofSeconds(1), Duration.ofSeconds(2), ignored -> {});
    }

    private static MinimalLobbyRuntime realtimeLobby(
            AuthorizedPlayerSessionQueue queue, RealtimeTicketProvisioner provisioner) {
        return new MinimalLobbyRuntime(
                queue,
                LobbyConfiguration.standard(),
                MatchConfiguration.defaults(20),
                provisioner,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                ignored -> {},
                () -> {});
    }

    private static MinimalLobbyRuntime lobby(
            AuthorizedPlayerSessionQueue queue, LobbyConfiguration configuration) {
        return new MinimalLobbyRuntime(
                queue, configuration, Duration.ofSeconds(1), Duration.ofSeconds(2), ignored -> {});
    }

    private static MinimalLobbyRuntime countdownLobby(AuthorizedPlayerSessionQueue queue) {
        MatchConfiguration matchConfiguration = new MatchConfiguration(2, 3, 2, 1, 2, 1, 2, 1, 1);
        return new MinimalLobbyRuntime(
                queue,
                LobbyConfiguration.standard(),
                matchConfiguration,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                ignored -> {});
    }

    private static void readyTwoPlayers(TestSession alpha, TestSession bravo)
            throws InterruptedException {
        sendSelect(alpha, 1L, LobbyTeam.GREEN);
        waitForResult(alpha, 1L);
        assertResult(alpha, 1L, 3L, LobbyCommandOutcome.APPLIED);

        sendSelect(bravo, 1L, LobbyTeam.BLUE);
        waitForResult(bravo, 1L);
        assertResult(bravo, 1L, 4L, LobbyCommandOutcome.APPLIED);

        sendReady(alpha, 2L, true);
        waitForResult(alpha, 2L);
        assertResult(alpha, 2L, 5L, LobbyCommandOutcome.APPLIED);

        sendReady(bravo, 2L, true);
        waitForResult(bravo, 2L);
        assertResult(bravo, 2L, 6L, LobbyCommandOutcome.APPLIED);
    }

    private static AuthorizedPlayerSessionQueue queue(int capacity) {
        return new AuthorizedPlayerSessionQueue(capacity, Duration.ofSeconds(1));
    }

    private static void enqueue(AuthorizedPlayerSessionQueue queue, TestSession transport) {
        AuthorizedPlayerSessionQueue.Reservation reservation = queue.tryReserve().orElseThrow();
        assertThat(
                        reservation.commit(
                                new AuthorizedPlayerSession(
                                        transport, HandleVerificationLevel.LOCAL_UNVERIFIED)))
                .isTrue();
    }

    private static void sendSelect(TestSession session, long requestId, LobbyTeam team) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.LOBBY_SELECT_TEAM,
                        LobbyProtocolCodec.encodeSelectTeam(
                                new LobbySelectTeamCommand(requestId, team))));
    }

    private static void sendReady(TestSession session, long requestId, boolean ready) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.LOBBY_SET_READY,
                        LobbyProtocolCodec.encodeSetReady(
                                new LobbySetReadyCommand(requestId, ready))));
    }

    private static void sendRealtimeTicketRequest(
            TestSession session, long requestId, int profileVersion) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.REALTIME_TICKET_REQUEST,
                        RealtimeTicketProtocolCodec.encodeRequest(
                                new RealtimeTicketRequest(requestId, profileVersion))));
    }

    private static List<SentMessage> realtimeTicketMessages(TestSession session) {
        return session.channel.sent().stream()
                .filter(message -> message.messageType() == MessageType.REALTIME_TICKET_RESULT)
                .toList();
    }

    private static ProtocolEnvelope envelope(
            TestSession session, MessageType messageType, byte[] payload) {
        return new ProtocolEnvelope(
                ProtocolVersion.CURRENT, messageType, session.sessionId(), 0L, payload);
    }

    private static void waitForResult(TestSession session, long requestId)
            throws InterruptedException {
        waitUntil(
                () ->
                        commandResultsUnchecked(session).stream()
                                .anyMatch(result -> result.requestId() == requestId));
    }

    private static void assertResult(
            TestSession session, long requestId, long revision, LobbyCommandOutcome outcome) {
        assertThat(commandResultsUnchecked(session))
                .contains(new LobbyCommandResult(requestId, revision, outcome));
    }

    private static List<LobbyCommandResult> commandResultsUnchecked(TestSession session) {
        return session.channel.sent().stream()
                .filter(message -> message.messageType() == MessageType.LOBBY_COMMAND_RESULT)
                .map(
                        message -> {
                            try {
                                return LobbyProtocolCodec.decodeCommandResult(message.payload());
                            } catch (LobbyProtocolException exception) {
                                throw new AssertionError(exception);
                            }
                        })
                .toList();
    }

    private static int preparationAssignmentCount(TestSession session) {
        return Math.toIntExact(
                session.channel.sent().stream()
                        .filter(
                                message ->
                                        message.messageType()
                                                == MessageType.PREPARATION_SPAWN_ASSIGNMENT)
                        .count());
    }

    private static PreparationSpawnAssignment latestPreparationAssignmentUnchecked(
            TestSession session) {
        SentMessage message =
                session.channel.sent().stream()
                        .filter(
                                candidate ->
                                        candidate.messageType()
                                                == MessageType.PREPARATION_SPAWN_ASSIGNMENT)
                        .reduce((first, second) -> second)
                        .orElseThrow();
        try {
            return PreparationSpawnProtocolCodec.decodeAssignment(message.payload());
        } catch (PreparationProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int preparationSnapshotMessageIndex(TestSession session) {
        List<SentMessage> messages = session.channel.sent();
        for (int index = 0; index < messages.size(); index++) {
            SentMessage message = messages.get(index);
            if (message.messageType() != MessageType.LOBBY_MATCH_SNAPSHOT) {
                continue;
            }
            try {
                if (LobbyMatchProtocolCodec.decodeSnapshot(message.payload()).phase()
                        == LobbyMatchPhase.PREPARATION) {
                    return index;
                }
            } catch (LobbyProtocolException exception) {
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("preparation snapshot was not sent");
    }

    private static int preparationAssignmentMessageIndex(TestSession session) {
        List<SentMessage> messages = session.channel.sent();
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index).messageType() == MessageType.PREPARATION_SPAWN_ASSIGNMENT) {
                return index;
            }
        }
        throw new AssertionError("preparation assignment was not sent");
    }

    private static int snapshotCount(TestSession session) {
        return Math.toIntExact(
                session.channel.sent().stream()
                        .filter(message -> message.messageType() == MessageType.LOBBY_SNAPSHOT)
                        .count());
    }

    private static void sendPreparationInput(TestSession session, PreparationInput input) {
        session.channel.completeMessage(
                envelope(
                        session,
                        MessageType.PREPARATION_INPUT,
                        PreparationMovementProtocolCodec.encodeInput(input)));
    }

    private static int preparationWorldSnapshotCount(TestSession session) {
        return Math.toIntExact(
                session.channel.sent().stream()
                        .filter(
                                message ->
                                        message.messageType() == MessageType.PREPARATION_SNAPSHOT)
                        .count());
    }

    private static int preparationWorldSnapshotMessageIndex(TestSession session) {
        List<SentMessage> sent = session.channel.sent();
        for (int index = 0; index < sent.size(); index++) {
            if (sent.get(index).messageType() == MessageType.PREPARATION_SNAPSHOT) {
                return index;
            }
        }
        return -1;
    }

    private static PreparationWorldSnapshot latestPreparationWorldSnapshotUnchecked(
            TestSession session) {
        List<SentMessage> snapshots =
                session.channel.sent().stream()
                        .filter(
                                message ->
                                        message.messageType() == MessageType.PREPARATION_SNAPSHOT)
                        .toList();
        try {
            return PreparationMovementProtocolCodec.decodeSnapshot(
                    snapshots.get(snapshots.size() - 1).payload());
        } catch (PreparationProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private static PreparationPlayerSnapshot player(
            PreparationWorldSnapshot snapshot, PlayerId playerId) {
        return snapshot.players().stream()
                .filter(player -> player.playerId().equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    private static int yawCentidegrees(double yawDegrees) {
        double normalized = yawDegrees % 360.0d;
        if (normalized >= 180.0d) {
            normalized -= 360.0d;
        } else if (normalized < -180.0d) {
            normalized += 360.0d;
        }
        long rounded = Math.round(normalized * 100.0d);
        return Math.toIntExact(rounded == 18_000L ? -18_000L : rounded);
    }

    private static int matchSnapshotCount(TestSession session, LobbyMatchPhase phase) {
        return Math.toIntExact(
                session.channel.sent().stream()
                        .filter(
                                message ->
                                        message.messageType() == MessageType.LOBBY_MATCH_SNAPSHOT)
                        .map(
                                message -> {
                                    try {
                                        return LobbyMatchProtocolCodec.decodeSnapshot(
                                                message.payload());
                                    } catch (LobbyProtocolException exception) {
                                        throw new AssertionError(exception);
                                    }
                                })
                        .filter(snapshot -> snapshot.phase() == phase)
                        .count());
    }

    private static Optional<SentMessage> latestMatchSnapshotMessage(TestSession session) {
        List<SentMessage> snapshots =
                session.channel.sent().stream()
                        .filter(
                                message -> message.messageType() == MessageType.LOBBY_MATCH_SNAPSHOT)
                        .toList();
        return snapshots.isEmpty()
                ? Optional.empty()
                : Optional.of(snapshots.get(snapshots.size() - 1));
    }

    private static LobbyMatchPhaseSnapshot latestMatchSnapshot(TestSession session)
            throws LobbyProtocolException {
        return LobbyMatchProtocolCodec.decodeSnapshot(
                latestMatchSnapshotMessage(session).orElseThrow().payload());
    }

    private static LobbyMatchPhaseSnapshot latestMatchSnapshotUnchecked(TestSession session) {
        try {
            return latestMatchSnapshot(session);
        } catch (LobbyProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Optional<SentMessage> latestSnapshotMessage(TestSession session) {
        List<SentMessage> snapshots =
                session.channel.sent().stream()
                        .filter(message -> message.messageType() == MessageType.LOBBY_SNAPSHOT)
                        .toList();
        return snapshots.isEmpty()
                ? Optional.empty()
                : Optional.of(snapshots.get(snapshots.size() - 1));
    }

    private static LobbySnapshot latestSnapshot(TestSession session) throws LobbyProtocolException {
        return LobbyProtocolCodec.decodeSnapshot(
                latestSnapshotMessage(session).orElseThrow().payload());
    }

    private static LobbySnapshot latestSnapshotUnchecked(TestSession session) {
        try {
            return latestSnapshot(session);
        } catch (LobbyProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            TimeUnit.MILLISECONDS.sleep(10L);
        }
    }

    private static PlayerId playerId(char first) {
        return new PlayerId("sf1_" + first + "a".repeat(51));
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static final class QueueEntropy implements RealtimeTicketEntropy {
        private final ArrayDeque<byte[]> values = new ArrayDeque<>();

        private QueueEntropy(byte[]... values) {
            Arrays.stream(values).forEach(value -> this.values.add(value.clone()));
        }

        @Override
        public byte[] randomBytes(int length) {
            byte[] value = values.removeFirst();
            if (value.length != length) {
                throw new IllegalStateException("test entropy length mismatch");
            }
            return value.clone();
        }
    }

    private record SentMessage(MessageType messageType, byte[] payload) {
        private SentMessage {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private static final class TestSession implements AuthenticatedPlayerSession {
        private final UUID sessionId;
        private final PlayerId playerId;
        private final CanonicalHandle handle;
        private final StubReliableChannel channel = new StubReliableChannel();
        private final AtomicInteger closes = new AtomicInteger();

        private TestSession(int suffix, PlayerId playerId, String handle) {
            sessionId = new UUID(0x4000L + suffix, 0x8000L + suffix);
            this.playerId = playerId;
            this.handle = new CanonicalHandle(handle);
        }

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public ServerId serverId() {
            return SERVER_ID;
        }

        @Override
        public PlayerId playerId() {
            return playerId;
        }

        @Override
        public SecureChannelBinding channelBinding() {
            return new SecureChannelBinding(new byte[SecureChannelBinding.BYTES]);
        }

        @Override
        public CanonicalHandle handle() {
            return handle;
        }

        @Override
        public ReliableChannel reliableChannel() {
            return channel;
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            closes.incrementAndGet();
            return channel.close();
        }

        private int closeCount() {
            return closes.get();
        }
    }

    private static final class StubReliableChannel implements ReliableChannel {
        private final CopyOnWriteArrayList<SentMessage> sent = new CopyOnWriteArrayList<>();
        private final ArrayDeque<Optional<ProtocolEnvelope>> inbound = new ArrayDeque<>();
        private final Object receiveLock = new Object();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger sequences = new AtomicInteger();
        private final AtomicReference<MessageType> failNextSend = new AtomicReference<>();
        private CompletableFuture<Optional<ProtocolEnvelope>> pendingReceive;

        @Override
        public CompletionStage<ReliableSendResult> send(MessageType messageType, byte[] payload) {
            if (!open.get() || failNextSend.compareAndSet(messageType, null)) {
                CompletableFuture<ReliableSendResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("send failed"));
                return failed;
            }
            sent.add(new SentMessage(messageType, payload));
            return CompletableFuture.completedFuture(
                    new ReliableSendResult(sequences.getAndIncrement()));
        }

        @Override
        public CompletionStage<Optional<ProtocolEnvelope>> receive() {
            synchronized (receiveLock) {
                if (!inbound.isEmpty()) {
                    return CompletableFuture.completedFuture(inbound.removeFirst());
                }
                if (!open.get()) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                if (pendingReceive != null) {
                    throw new IllegalStateException("only one receive may be active");
                }
                pendingReceive = new CompletableFuture<>();
                return pendingReceive.minimalCompletionStage();
            }
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public CompletionStage<Void> close() {
            CompletableFuture<Optional<ProtocolEnvelope>> receiveToComplete;
            synchronized (receiveLock) {
                open.set(false);
                receiveToComplete = pendingReceive;
                pendingReceive = null;
                inbound.clear();
            }
            if (receiveToComplete != null) {
                receiveToComplete.complete(Optional.empty());
            }
            return CompletableFuture.completedFuture(null);
        }

        private List<SentMessage> sent() {
            return List.copyOf(sent);
        }

        private void failNextSend(MessageType messageType) {
            if (!failNextSend.compareAndSet(null, messageType)) {
                throw new IllegalStateException("a send failure is already scheduled");
            }
        }

        private void completeEof() {
            completeInbound(Optional.empty());
        }

        private void completeMessage(ProtocolEnvelope message) {
            completeInbound(Optional.of(message));
        }

        private void completeInbound(Optional<ProtocolEnvelope> message) {
            CompletableFuture<Optional<ProtocolEnvelope>> receiveToComplete;
            synchronized (receiveLock) {
                receiveToComplete = pendingReceive;
                pendingReceive = null;
                if (receiveToComplete == null) {
                    inbound.addLast(message);
                    return;
                }
            }
            receiveToComplete.complete(message);
        }
    }
}
