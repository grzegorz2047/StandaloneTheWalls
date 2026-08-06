from pathlib import Path

path = Path(
    "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/identity/session/MinimalLobbyRuntimeTest.java"
)
text = path.read_text(encoding="utf-8")
marker = "LobbyMatchPhaseSnapshot openCombat = latestMatchSnapshotUnchecked(alpha);"
if marker not in text:
    old = '''            PreparationWorldSnapshot moved = null;
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
            PreparationWorldSnapshot authoritativeMovement = moved;'''
    new = '''            assertThat(lobby.offerSimulationTick(6L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                            == LobbyMatchPhase.PREPARATION
                                    && latestMatchSnapshotUnchecked(alpha).ticksRemaining() == 1L);
            LobbyMatchPhaseSnapshot finalPreparation = latestMatchSnapshotUnchecked(alpha);
            assertThat(finalPreparation).isEqualTo(latestMatchSnapshotUnchecked(bravo));
            assertThat(finalPreparation.authoritativeTick()).isEqualTo(6L);
            assertThat(matchSnapshotCount(alpha, LobbyMatchPhase.PREPARATION)).isEqualTo(2);
            assertThat(matchSnapshotCount(bravo, LobbyMatchPhase.PREPARATION)).isEqualTo(2);
            waitUntil(
                    () ->
                            latestPreparationWorldSnapshotUnchecked(alpha).authoritativeTick()
                                    >= 6L);

            assertThat(lobby.offerSimulationTick(7L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.WALLS_OPENING);
            LobbyMatchPhaseSnapshot opening = latestMatchSnapshotUnchecked(alpha);
            assertThat(opening).isEqualTo(latestMatchSnapshotUnchecked(bravo));
            assertThat(opening.authoritativeTick()).isEqualTo(7L);
            assertThat(opening.ticksRemaining()).isOne();
            assertThat(matchSnapshotCount(alpha, LobbyMatchPhase.WALLS_OPENING)).isOne();
            assertThat(matchSnapshotCount(bravo, LobbyMatchPhase.WALLS_OPENING)).isOne();

            assertThat(lobby.offerSimulationTick(8L)).isTrue();
            waitUntil(
                    () ->
                            latestMatchSnapshotUnchecked(alpha).phase()
                                    == LobbyMatchPhase.OPEN_COMBAT);
            LobbyMatchPhaseSnapshot openCombat = latestMatchSnapshotUnchecked(alpha);
            assertThat(openCombat).isEqualTo(latestMatchSnapshotUnchecked(bravo));
            assertThat(openCombat.authoritativeTick()).isEqualTo(8L);
            assertThat(openCombat.ticksRemaining()).isEqualTo(2L);
            assertThat(matchSnapshotCount(alpha, LobbyMatchPhase.OPEN_COMBAT)).isOne();
            assertThat(matchSnapshotCount(bravo, LobbyMatchPhase.OPEN_COMBAT)).isOne();
            waitUntil(
                    () ->
                            latestPreparationWorldSnapshotUnchecked(alpha).authoritativeTick()
                                    >= 8L);
            PreparationWorldSnapshot authoritativeMovement =
                    latestPreparationWorldSnapshotUnchecked(alpha);'''
    if old not in text:
        raise SystemExit("two-client movement phase anchor not found")
    text = text.replace(old, new, 1)

old_disconnect = '''            waitUntil(() -> lobby.matchSnapshot().phase().name().equals("PREPARATION"));
            LobbyMatchPhaseSnapshot afterDisconnect = latestMatchSnapshotUnchecked(alpha);
            assertThat(afterDisconnect.phase()).isEqualTo(LobbyMatchPhase.PREPARATION);
            assertThat(afterDisconnect.connectedPlayers()).isEqualTo(1);
            assertThat(afterDisconnect.revision()).isEqualTo(preparation.revision() + 1L);'''
new_disconnect = '''            waitUntil(() -> lobby.matchSnapshot().phase().name().equals("OPEN_COMBAT"));
            LobbyMatchPhaseSnapshot afterDisconnect = latestMatchSnapshotUnchecked(alpha);
            assertThat(afterDisconnect.phase()).isEqualTo(LobbyMatchPhase.OPEN_COMBAT);
            assertThat(afterDisconnect.connectedPlayers()).isEqualTo(1);
            assertThat(afterDisconnect.revision()).isEqualTo(openCombat.revision() + 1L);'''
if old_disconnect in text:
    text = text.replace(old_disconnect, new_disconnect, 1)
elif new_disconnect not in text:
    raise SystemExit("post-disconnect phase anchor not found")

path.write_text(text, encoding="utf-8")
