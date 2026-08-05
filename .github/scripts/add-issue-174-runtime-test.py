from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


path = "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/identity/session/MinimalLobbyRuntimeTest.java"

replace_once(
    path,
    """import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
""",
    """import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationMovementProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationProtocolException;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnProtocolCodec;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;
""",
)

replace_once(
    path,
    """            assertThat(preparationSnapshotMessageIndex(alpha))
                    .isLessThan(preparationAssignmentMessageIndex(alpha));
            assertThat(preparationSnapshotMessageIndex(bravo))
                    .isLessThan(preparationAssignmentMessageIndex(bravo));

            int rosterSnapshotsBeforeLockedCommand = snapshotCount(alpha);
""",
    """            assertThat(preparationSnapshotMessageIndex(alpha))
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

            int rosterSnapshotsBeforeLockedCommand = snapshotCount(alpha);
""",
)

replace_once(
    path,
    """            assertThat(lobby.offerSimulationTick(6L)).isTrue();
            waitUntil(() -> lobby.matchSnapshot().phase().name().equals(\"PREPARATION\"));
            assertThat(latestMatchSnapshotUnchecked(alpha).revision())
                    .isEqualTo(preparation.revision());
            assertThat(preparationAssignmentCount(alpha)).isOne();
            assertThat(preparationAssignmentCount(bravo)).isOne();
""",
    """            PreparationPlayerSnapshot initialAlpha = player(initialWorld, alpha.playerId());
            PreparationPlayerSnapshot initialBravo = player(initialWorld, bravo.playerId());
            sendPreparationInput(
                    alpha,
                    new PreparationInput(
                            preparation.roundNumber(),
                            1L,
                            127,
                            0,
                            yawCentidegrees(alphaAssignment.yawDegrees()),
                            0));

            PreparationWorldSnapshot moved = null;
            long movementTick = 6L;
            while (movementTick <= 12L && moved == null) {
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
            PreparationPlayerSnapshot movedAlpha =
                    player(authoritativeMovement, alpha.playerId());
            PreparationPlayerSnapshot unmovedBravo =
                    player(authoritativeMovement, bravo.playerId());
            assertThat(movedAlpha.lastProcessedInputSequence()).isEqualTo(1L);
            assertThat(
                            Math.hypot(
                                    movedAlpha.xMillimetres() - initialAlpha.xMillimetres(),
                                    movedAlpha.zMillimetres() - initialAlpha.zMillimetres()))
                    .isGreaterThan(0.0d)
                    .isLessThanOrEqualTo(1_750.0d);
            assertThat(unmovedBravo).isEqualTo(initialBravo);

            bravo.channel.completeEof();
            waitUntil(() -> lobby.memberCount() == 1 && bravo.closeCount() == 1);
            long removalSnapshotTick = authoritativeMovement.authoritativeTick() + 2L;
            for (long tick = authoritativeMovement.authoritativeTick() + 1L;
                    tick <= removalSnapshotTick;
                    tick++) {
                assertThat(lobby.offerSimulationTick(tick)).isTrue();
            }
            waitUntil(
                    () ->
                            latestPreparationWorldSnapshotUnchecked(alpha)
                                            .authoritativeTick()
                                    >= removalSnapshotTick);
            assertThat(latestPreparationWorldSnapshotUnchecked(alpha).players())
                    .extracting(PreparationPlayerSnapshot::playerId)
                    .containsExactly(alpha.playerId());

            waitUntil(() -> lobby.matchSnapshot().phase().name().equals(\"PREPARATION\"));
            assertThat(latestMatchSnapshotUnchecked(alpha).revision())
                    .isEqualTo(preparation.revision());
            assertThat(preparationAssignmentCount(alpha)).isOne();
            assertThat(preparationAssignmentCount(bravo)).isOne();
""",
)

replace_once(
    path,
    """    private static int matchSnapshotCount(TestSession session, LobbyMatchPhase phase) {
""",
    """    private static void sendPreparationInput(TestSession session, PreparationInput input) {
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
""",
)
