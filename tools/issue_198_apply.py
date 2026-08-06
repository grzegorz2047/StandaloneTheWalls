from pathlib import Path


def replace_once(path: str, old: str, new: str, marker: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {marker}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


# Client match phase presentation.
replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java",
    '''                    case PREPARATION -> messages.text("direct.lobby.phase.preparation");
                };''',
    '''                    case PREPARATION -> messages.text("direct.lobby.phase.preparation");
                    case WALLS_OPENING ->
                            messages.text(
                                    "direct.lobby.phase.walls_opening", snapshot.ticksRemaining());
                    case OPEN_COMBAT -> messages.text("direct.lobby.phase.open_combat");
                };''',
    "case WALLS_OPENING",
)

translations = {
    "client/src/main/resources/i18n/messages_en.properties": (
        "direct.lobby.phase.preparation=Preparation has started. Team and readiness controls are locked.\n",
        "direct.lobby.phase.preparation=Preparation has started. Team and readiness controls are locked.\n"
        "direct.lobby.phase.walls_opening=Central walls open in {0} tick(s).\n"
        "direct.lobby.phase.open_combat=Central walls are open. Open combat has started.\n",
    ),
    "client/src/main/resources/i18n/messages_pl.properties": (
        "direct.lobby.phase.preparation=Rozpoczęła się faza przygotowania. Zmiana drużyny i gotowości jest zablokowana.\n",
        "direct.lobby.phase.preparation=Rozpoczęła się faza przygotowania. Zmiana drużyny i gotowości jest zablokowana.\n"
        "direct.lobby.phase.walls_opening=Centralne ściany otworzą się za {0} ticków.\n"
        "direct.lobby.phase.open_combat=Centralne ściany są otwarte. Rozpoczęła się otwarta walka.\n",
    ),
}
for filename, (anchor, replacement) in translations.items():
    replace_once(filename, anchor, replacement, "direct.lobby.phase.walls_opening=")

simulation = "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java"
replace_once(
    simulation,
    '''import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;''',
    '''import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    simulation,
    '''    private long lastAdvancedTick;
''',
    '''    private long lastAdvancedTick;
    private PreparationBarrierPolicy barrierPolicy = PreparationBarrierPolicy.CLOSED;
''',
    "private PreparationBarrierPolicy barrierPolicy",
)
replace_once(
    simulation,
    '''        PreparationMovementSimulation simulation =
                new PreparationMovementSimulation(roundNumber, initialTick);
        for (Map.Entry<PlayerId, PreparationSpawnAssignment> entry :
''',
    '''        PreparationMovementSimulation simulation =
                new PreparationMovementSimulation(roundNumber, initialTick);
        PreparationWorldBounds worldBounds = worldBounds(verifiedMap.regions().values());
        for (Map.Entry<PlayerId, PreparationSpawnAssignment> entry :
''',
    "PreparationWorldBounds worldBounds = worldBounds",
)
replace_once(
    simulation,
    '''                    PlayerState.atSpawn(
                            assignment,
                            region,
                            verifiedMap.supportMap(),
                            verifiedMap.obstacleMap());''',
    '''                    PlayerState.atSpawn(
                            assignment,
                            region,
                            worldBounds,
                            verifiedMap.supportMap(),
                            verifiedMap.obstacleMap());''',
    "                            worldBounds,\n                            verifiedMap.supportMap()",
)
replace_once(
    simulation,
    '''    public long lastAdvancedTick() {
        return lastAdvancedTick;
    }

    public boolean remove(PlayerId playerId) {''',
    '''    public long lastAdvancedTick() {
        return lastAdvancedTick;
    }

    public PreparationBarrierPolicy barrierPolicy() {
        return barrierPolicy;
    }

    public boolean remove(PlayerId playerId) {''',
    "public PreparationBarrierPolicy barrierPolicy()",
)
replace_once(
    simulation,
    '''    public PreparationWorldSnapshot advanceTick(
            long authoritativeTick, Map<PlayerId, PreparationInput> latestInputs) {
        if (authoritativeTick <= lastAdvancedTick) {
            throw new IllegalArgumentException("authoritativeTick must advance monotonically");
        }
        Map<PlayerId, PreparationInput> inputs =
                Map.copyOf(Objects.requireNonNull(latestInputs, "latestInputs"));
        for (Map.Entry<PlayerId, PreparationInput> entry : inputs.entrySet()) {
            PlayerState player = players.get(Objects.requireNonNull(entry.getKey(), "playerId"));
            if (player == null) {
                throw new IllegalArgumentException(
                        "input references an unknown preparation player");
            }
            PreparationInput input = Objects.requireNonNull(entry.getValue(), "input");
            if (input.roundNumber() != roundNumber) {
                throw new IllegalArgumentException("input round does not match the simulation");
            }
            player.accept(input);
        }
        for (PlayerState player : players.values()) {
            player.advance();
        }
        lastAdvancedTick = authoritativeTick;
        return snapshot(authoritativeTick);
    }
''',
    '''    public PreparationWorldSnapshot advanceTick(
            long authoritativeTick, Map<PlayerId, PreparationInput> latestInputs) {
        return advanceTick(authoritativeTick, latestInputs, barrierPolicy);
    }

    public PreparationWorldSnapshot advanceTick(
            long authoritativeTick,
            Map<PlayerId, PreparationInput> latestInputs,
            PreparationBarrierPolicy requestedBarrierPolicy) {
        if (authoritativeTick <= lastAdvancedTick) {
            throw new IllegalArgumentException("authoritativeTick must advance monotonically");
        }
        PreparationBarrierPolicy nextBarrierPolicy =
                Objects.requireNonNull(requestedBarrierPolicy, "requestedBarrierPolicy");
        if (barrierPolicy == PreparationBarrierPolicy.OPEN
                && nextBarrierPolicy == PreparationBarrierPolicy.CLOSED) {
            throw new IllegalArgumentException(
                    "central barriers cannot close again during an authoritative round");
        }
        Map<PlayerId, PreparationInput> inputs =
                Map.copyOf(Objects.requireNonNull(latestInputs, "latestInputs"));
        for (Map.Entry<PlayerId, PreparationInput> entry : inputs.entrySet()) {
            PlayerState player = players.get(Objects.requireNonNull(entry.getKey(), "playerId"));
            if (player == null) {
                throw new IllegalArgumentException(
                        "input references an unknown preparation player");
            }
            PreparationInput input = Objects.requireNonNull(entry.getValue(), "input");
            if (input.roundNumber() != roundNumber) {
                throw new IllegalArgumentException("input round does not match the simulation");
            }
            player.accept(input);
        }
        barrierPolicy = nextBarrierPolicy;
        for (PlayerState player : players.values()) {
            player.advance(barrierPolicy);
        }
        lastAdvancedTick = authoritativeTick;
        return snapshot(authoritativeTick);
    }
''',
    "PreparationBarrierPolicy requestedBarrierPolicy",
)
replace_once(
    simulation,
    '''    private static TeamId domainTeam(LobbyTeam team) {''',
    '''    private static PreparationWorldBounds worldBounds(
            Iterable<PreparationRegionBounds> regions) {
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        boolean found = false;
        for (PreparationRegionBounds region : regions) {
            PreparationRegionBounds candidate = Objects.requireNonNull(region, "region");
            found = true;
            minimumX = Math.min(minimumX, candidate.minimumXMillimetres());
            minimumY = Math.min(minimumY, candidate.minimumYMillimetres());
            minimumZ = Math.min(minimumZ, candidate.minimumZMillimetres());
            maximumX = Math.max(maximumX, candidate.maximumXMillimetres());
            maximumY = Math.max(maximumY, candidate.maximumYMillimetres());
            maximumZ = Math.max(maximumZ, candidate.maximumZMillimetres());
        }
        if (!found) {
            throw new IllegalArgumentException("authoritative map has no movement regions");
        }
        return new PreparationWorldBounds(
                new MapVector3(
                        minimumX / 1_000.0d, minimumY / 1_000.0d, minimumZ / 1_000.0d),
                new MapVector3(
                        maximumX / 1_000.0d, maximumY / 1_000.0d, maximumZ / 1_000.0d));
    }

    private static TeamId domainTeam(LobbyTeam team) {''',
    "private static PreparationWorldBounds worldBounds(",
)
replace_once(
    simulation,
    '''        private final PreparationRegionBounds region;
        private final PreparationSupportMap supportMap;''',
    '''        private final PreparationRegionBounds region;
        private final PreparationWorldBounds worldBounds;
        private final PreparationSupportMap supportMap;''',
    "private final PreparationWorldBounds worldBounds;",
)
replace_once(
    simulation,
    '''        private PlayerState(
                PreparationRegionBounds region,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap,''',
    '''        private PlayerState(
                PreparationRegionBounds region,
                PreparationWorldBounds worldBounds,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap,''',
    "                PreparationWorldBounds worldBounds,\n                PreparationSupportMap supportMap",
)
replace_once(
    simulation,
    '''            this.region = Objects.requireNonNull(region, "region");
            this.supportMap = Objects.requireNonNull(supportMap, "supportMap");''',
    '''            this.region = Objects.requireNonNull(region, "region");
            this.worldBounds = Objects.requireNonNull(worldBounds, "worldBounds");
            this.supportMap = Objects.requireNonNull(supportMap, "supportMap");''',
    "this.worldBounds = Objects.requireNonNull(worldBounds",
)
replace_once(
    simulation,
    '''        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap) {
            return new PlayerState(
                    region,
                    supportMap,''',
    '''        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationWorldBounds worldBounds,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap) {
            return new PlayerState(
                    region,
                    worldBounds,
                    supportMap,''',
    "                PreparationWorldBounds worldBounds,\n                PreparationSupportMap supportMap",
)
replace_once(
    simulation,
    '''        private void advance() {
            PreparationInput input = activeInput;
            if (input != null) {
                applyRequestedPosture(input.crouching());
                advanceHorizontal(input);
            }''',
    '''        private void advance(PreparationBarrierPolicy barrierPolicy) {
            PreparationInput input = activeInput;
            if (input != null) {
                applyRequestedPosture(input.crouching(), barrierPolicy);
                advanceHorizontal(input, barrierPolicy);
            }''',
    "private void advance(PreparationBarrierPolicy barrierPolicy)",
)
replace_once(
    simulation,
    '''                            vertical.heightMetres(),
                            crouching);''',
    '''                            vertical.heightMetres(),
                            crouching,
                            barrierPolicy);''',
    "                            crouching,\n                            barrierPolicy);",
)
replace_once(
    simulation,
    '''        private void applyRequestedPosture(boolean requestedCrouching) {''',
    '''        private void applyRequestedPosture(
                boolean requestedCrouching, PreparationBarrierPolicy barrierPolicy) {''',
    "boolean requestedCrouching, PreparationBarrierPolicy barrierPolicy",
)
replace_once(
    simulation,
    '''                            zMillimetres / 1_000.0d,
                            false)) {''',
    '''                            zMillimetres / 1_000.0d,
                            false,
                            barrierPolicy)) {''',
    "                            false,\n                            barrierPolicy))",
)
replace_once(
    simulation,
    '''        private void advanceHorizontal(PreparationInput input) {''',
    '''        private void advanceHorizontal(
                PreparationInput input, PreparationBarrierPolicy barrierPolicy) {''',
    "PreparationInput input, PreparationBarrierPolicy barrierPolicy",
)
replace_once(
    simulation,
    '''            double targetX =
                    region.clampX(xMillimetres + step * ((forward * forwardX) + (right * rightX)));
            double targetZ =
                    region.clampZ(zMillimetres + step * ((forward * forwardZ) + (right * rightZ)));''',
    '''            double requestedX =
                    xMillimetres + step * ((forward * forwardX) + (right * rightX));
            double requestedZ =
                    zMillimetres + step * ((forward * forwardZ) + (right * rightZ));
            double targetX =
                    barrierPolicy == PreparationBarrierPolicy.OPEN
                            ? worldBounds.clampX(requestedX / 1_000.0d) * 1_000.0d
                            : region.clampX(requestedX);
            double targetZ =
                    barrierPolicy == PreparationBarrierPolicy.OPEN
                            ? worldBounds.clampZ(requestedZ / 1_000.0d) * 1_000.0d
                            : region.clampZ(requestedZ);''',
    "double requestedX =",
)
replace_once(
    simulation,
    '''            if (tryMove(targetX, targetZ)) {''',
    '''            if (tryMove(targetX, targetZ, barrierPolicy)) {''',
    "if (tryMove(targetX, targetZ, barrierPolicy))",
)
replace_once(
    simulation,
    '''                if (!tryMove(targetX, originalZ)) {
                    tryMove(originalX, targetZ);
                }
            } else if (!tryMove(originalX, targetZ)) {
                tryMove(targetX, originalZ);
            }
        }

        private boolean tryMove(double targetX, double targetZ) {''',
    '''                if (!tryMove(targetX, originalZ, barrierPolicy)) {
                    tryMove(originalX, targetZ, barrierPolicy);
                }
            } else if (!tryMove(originalX, targetZ, barrierPolicy)) {
                tryMove(targetX, originalZ, barrierPolicy);
            }
        }

        private boolean tryMove(
                double targetX,
                double targetZ,
                PreparationBarrierPolicy barrierPolicy) {''',
    "double targetZ,\n                PreparationBarrierPolicy barrierPolicy)",
)
replace_once(
    simulation,
    '''                    targetZ / 1_000.0d,
                    crouching)) {''',
    '''                    targetZ / 1_000.0d,
                    crouching,
                    barrierPolicy)) {''',
    "                    crouching,\n                    barrierPolicy))",
)

runtime = "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java"
replace_once(
    runtime,
    '''import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;''',
    '''import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    runtime,
    '''        PreparationWorldSnapshot snapshot = movement.advanceTick(authoritativeTick, latestInputs);''',
    '''        PreparationBarrierPolicy barrierPolicy =
                matchCoordinator.snapshot().phase() == MatchPhase.OPEN_COMBAT
                        ? PreparationBarrierPolicy.OPEN
                        : PreparationBarrierPolicy.CLOSED;
        PreparationWorldSnapshot snapshot =
                movement.advanceTick(authoritativeTick, latestInputs, barrierPolicy);''',
    "movement.advanceTick(authoritativeTick, latestInputs, barrierPolicy)",
)

obstacle_test = "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/PreparationObstacleMapTest.java"
replace_once(
    obstacle_test,
    '''    @Test
    void rejectsMoreThanTheBoundedObstacleCount() {''',
    '''    @Test
    void opensOnlyTheTwoExactCentralBarrierNames() {
        PreparationObstacleMap map =
                new PreparationObstacleMap(
                        List.of(
                                box(
                                        PreparationObstacleMap.CENTRAL_WALL_X_NAME,
                                        -0.05d,
                                        0.0d,
                                        -2.0d,
                                        0.05d,
                                        5.0d,
                                        2.0d),
                                box(
                                        "PermanentObstacleCollision",
                                        3.95d,
                                        0.0d,
                                        -2.0d,
                                        4.05d,
                                        5.0d,
                                        2.0d),
                                box(
                                        "CentralWallXCollisionObstacleCollision",
                                        7.95d,
                                        0.0d,
                                        -2.0d,
                                        8.05d,
                                        5.0d,
                                        2.0d)));

        assertThat(map.centralBarrierCount()).isOne();
        assertThat(
                        map.permitsMovement(
                                -1.0d,
                                0.5d,
                                0.0d,
                                1.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.CLOSED))
                .isFalse();
        assertThat(
                        map.permitsMovement(
                                -1.0d,
                                0.5d,
                                0.0d,
                                1.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isTrue();
        assertThat(
                        map.permitsMovement(
                                3.0d,
                                0.5d,
                                0.0d,
                                5.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isFalse();
        assertThat(
                        map.permitsMovement(
                                7.0d,
                                0.5d,
                                0.0d,
                                9.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isFalse();
    }

    @Test
    void rejectsMoreThanTheBoundedObstacleCount() {''',
    "void opensOnlyTheTwoExactCentralBarrierNames()",
)

world_bounds_test = Path(
    "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/PreparationWorldBoundsTest.java"
)
if not world_bounds_test.exists():
    world_bounds_test.write_text(
        '''package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreparationWorldBoundsTest {
    @Test
    void coversAllVerifiedRegionsAndClampsFiniteHorizontalCoordinates() {
        PreparationWorldBounds bounds =
                PreparationWorldBounds.fromRegions(
                        List.of(
                                new PreparationRegion(
                                        PreparationTeam.RED,
                                        new MapVector3(-10.0d, -1.0d, -8.0d),
                                        new MapVector3(-0.5d, 6.0d, 8.0d)),
                                new PreparationRegion(
                                        PreparationTeam.BLUE,
                                        new MapVector3(-0.5d, -2.0d, -12.0d),
                                        new MapVector3(11.0d, 7.0d, 12.0d))));

        assertThat(bounds.minimum()).isEqualTo(new MapVector3(-10.0d, -2.0d, -12.0d));
        assertThat(bounds.maximum()).isEqualTo(new MapVector3(11.0d, 7.0d, 12.0d));
        assertThat(bounds.clampX(-20.0d)).isEqualTo(-10.0d);
        assertThat(bounds.clampX(4.0d)).isEqualTo(4.0d);
        assertThat(bounds.clampZ(20.0d)).isEqualTo(12.0d);
        assertThat(bounds.contains(new MapVector3(0.0d, 0.5d, 0.0d))).isTrue();
    }

    @Test
    void rejectsEmptyRegionSetsAndNonFiniteClampInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PreparationWorldBounds.fromRegions(List.of()))
                .withMessageContaining("at least one");
        PreparationWorldBounds bounds =
                new PreparationWorldBounds(
                        new MapVector3(-1.0d, -1.0d, -1.0d),
                        new MapVector3(1.0d, 1.0d, 1.0d));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bounds.clampX(Double.NaN))
                .withMessageContaining("finite");
    }
}
''',
        encoding="utf-8",
    )

server_test = "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationObstacleMovementSimulationTest.java"
replace_once(
    server_test,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleBox;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleBox;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    server_test,
    '''    @Test
    void rejectsASpawnWithoutAuthoritativeStandingClearance() {''',
    '''    @Test
    void opensTheCentralBarrierAndWorldBoundsFromTheFirstOpenCombatTick() {
        PreparationObstacleMap obstacles =
                new PreparationObstacleMap(
                        List.of(
                                obstacle(
                                        PreparationObstacleMap.CENTRAL_WALL_X_NAME,
                                        -0.05d,
                                        0.0d,
                                        -2.0d,
                                        0.05d,
                                        5.0d,
                                        2.0d)));
        PreparationMapDefinition map =
                new PreparationMapDefinition(
                        "minimal_preparation",
                        MAP_DIGEST,
                        List.of(
                                new PreparationSpawnPoint(
                                        0, TeamId.RED, -0.8d, 0.5d, 0.0d, 0.0d)),
                        Map.of(
                                TeamId.RED,
                                new PreparationRegionBounds(
                                        TeamId.RED,
                                        -2_000,
                                        -1_000,
                                        -2_000,
                                        -400,
                                        6_000,
                                        2_000),
                                TeamId.BLUE,
                                new PreparationRegionBounds(
                                        TeamId.BLUE,
                                        -400,
                                        -1_000,
                                        -2_000,
                                        2_000,
                                        6_000,
                                        2_000)),
                        GROUND,
                        obstacles);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        4L,
                        2L,
                        "minimal_preparation",
                        MAP_DIGEST,
                        LobbyTeam.RED,
                        0,
                        -0.8d,
                        0.5d,
                        0.0d,
                        0.0d);
        PreparationMovementSimulation simulation =
                PreparationMovementSimulation.start(
                        2L, 10L, map, Map.of(ALPHA, assignment));

        PreparationPlayerSnapshot closed =
                player(
                        simulation.advanceTick(
                                11L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 1L, 127, 0, true, false, false, 0, 0)),
                                PreparationBarrierPolicy.CLOSED));
        PreparationPlayerSnapshot firstOpen =
                player(
                        simulation.advanceTick(
                                12L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 2L, 127, 0, true, false, false, 0, 0)),
                                PreparationBarrierPolicy.OPEN));
        PreparationPlayerSnapshot beyondTeamRegion =
                player(
                        simulation.advanceTick(
                                13L,
                                Map.of(
                                        ALPHA,
                                        new PreparationInput(
                                                2L, 3L, 127, 0, true, false, false, 0, 0)),
                                PreparationBarrierPolicy.OPEN));

        assertThat(closed.xMillimetres()).isEqualTo(-800);
        assertThat(firstOpen.xMillimetres()).isEqualTo(-400);
        assertThat(beyondTeamRegion.xMillimetres()).isZero();
        assertThat(simulation.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                simulation.advanceTick(
                                        14L, Map.of(), PreparationBarrierPolicy.CLOSED))
                .withMessageContaining("cannot close");
    }

    @Test
    void rejectsASpawnWithoutAuthoritativeStandingClearance() {''',
    "void opensTheCentralBarrierAndWorldBoundsFromTheFirstOpenCombatTick()",
)
