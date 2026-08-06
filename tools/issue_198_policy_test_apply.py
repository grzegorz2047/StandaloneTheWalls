from pathlib import Path


def replace_once(path: str, old: str, new: str, marker: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {marker}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


obstacle_test = Path(
    "map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/PreparationObstacleMapTest.java"
)
text = obstacle_test.read_text(encoding="utf-8")
marker = "map.hasPlayerClearance(0.0d, 0.5d, 0.0d, false, PreparationBarrierPolicy.OPEN)"
if marker not in text:
    anchor = '''        assertThat(map.centralBarrierCount()).isOne();
        assertThat(
                        map.permitsMovement('''
    replacement = '''        assertThat(map.centralBarrierCount()).isOne();
        assertThat(
                        map.hasPlayerClearance(
                                0.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.CLOSED))
                .isFalse();
        assertThat(
                        map.hasPlayerClearance(
                                0.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isTrue();
        assertThat(
                        map.limitUpwardMovement(
                                0.0d,
                                0.0d,
                                0.5d,
                                1.0d,
                                false,
                                PreparationBarrierPolicy.CLOSED))
                .isEqualTo(0.5d);
        assertThat(
                        map.limitUpwardMovement(
                                0.0d,
                                0.0d,
                                0.5d,
                                1.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isEqualTo(1.0d);
        assertThat(
                        map.hasPlayerClearance(
                                4.0d,
                                0.5d,
                                0.0d,
                                false,
                                PreparationBarrierPolicy.OPEN))
                .isFalse();
        assertThat(
                        map.permitsMovement('''
    if anchor not in text:
        raise SystemExit("obstacle policy assertion anchor not found")
    obstacle_test.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")

client_test = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationObstacleSlidingControllerTest.java"
replace_once(
    client_test,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationMapSpawn;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    client_test,
    '''    private static VerifiedPreparationScene broadCentralWallScene()
            throws PreparationSceneLoadException {''',
    '''    @Test
    void openPolicyCrossesTheVerifiedCentralWallButKeepsTheSameSupport()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        VerifiedPreparationScene scene = broadCentralWallScene();
        PreparationPlayerState open =
                PreparationPlayerState.atAuthoritativeSpawn(scene)
                        .withBarrierPolicy(PreparationBarrierPolicy.OPEN);
        PreparationCollisionWorld collisions =
                PreparationCollisionWorld.load(new DesktopAssetManager(true), scene);

        PreparationPlayerState crossed =
                PreparationMovementController.move(open, collisions, 1.0d, 0.0d, 0.1d);

        assertThat(crossed.position().x()).isCloseTo(-0.36d, within(0.000001d));
        assertThat(crossed.position().z()).isEqualTo(-2.0d);
        assertThat(crossed.position().y()).isEqualTo(0.5d);
        assertThat(crossed.grounded()).isTrue();
        assertThat(crossed.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
    }

    private static VerifiedPreparationScene broadCentralWallScene()
            throws PreparationSceneLoadException {''',
    "void openPolicyCrossesTheVerifiedCentralWallButKeepsTheSameSupport()",
)
