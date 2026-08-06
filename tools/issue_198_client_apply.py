from pathlib import Path


def replace_once(path: str, old: str, new: str, marker: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {marker}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


scene = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/VerifiedPreparationScene.java"
replace_once(
    scene,
    '''import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;''',
    '''import java.util.List;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.Glb2Document;''',
    "import java.util.List;",
)
replace_once(
    scene,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;",
)
replace_once(
    scene,
    '''    private final PreparationObstacleMap obstacleMap;
    private final PreparationRegion region;''',
    '''    private final PreparationObstacleMap obstacleMap;
    private final PreparationWorldBounds worldBounds;
    private final PreparationRegion region;''',
    "private final PreparationWorldBounds worldBounds;",
)
replace_once(
    scene,
    '''    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationSupportMap supportMap,
            PreparationObstacleMap obstacleMap,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");''',
    '''    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationSupportMap supportMap,
            PreparationObstacleMap obstacleMap,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this(
                mapId,
                mapSha256,
                sceneGlb,
                collisionGlb,
                sceneDocument,
                collisionDocument,
                supportMap,
                obstacleMap,
                PreparationWorldBounds.fromRegions(List.of(region)),
                region,
                spawn);
    }

    VerifiedPreparationScene(
            String mapId,
            byte[] mapSha256,
            byte[] sceneGlb,
            byte[] collisionGlb,
            Glb2Document sceneDocument,
            Glb2Document collisionDocument,
            PreparationSupportMap supportMap,
            PreparationObstacleMap obstacleMap,
            PreparationWorldBounds worldBounds,
            PreparationRegion region,
            PreparationMapSpawn spawn) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");''',
    "PreparationWorldBounds.fromRegions(List.of(region))",
)
replace_once(
    scene,
    '''        this.obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
        this.region = Objects.requireNonNull(region, "region");''',
    '''        this.obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
        this.worldBounds = Objects.requireNonNull(worldBounds, "worldBounds");
        this.region = Objects.requireNonNull(region, "region");''',
    "this.worldBounds = Objects.requireNonNull(worldBounds",
)
replace_once(
    scene,
    '''    public PreparationRegion region() {
        return region;
    }
''',
    '''    public PreparationWorldBounds worldBounds() {
        return worldBounds;
    }

    public PreparationRegion region() {
        return region;
    }
''',
    "public PreparationWorldBounds worldBounds()",
)

loader = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationSceneLoader.java"
replace_once(
    loader,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleException;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.PreparationTeam;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleException;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;",
)
replace_once(
    loader,
    '''                supportMap,
                obstacleMap,
                region,
                spawn);''',
    '''                supportMap,
                obstacleMap,
                PreparationWorldBounds.fromRegions(gameplay.regions()),
                region,
                spawn);''',
    "PreparationWorldBounds.fromRegions(gameplay.regions())",
)

player = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPlayerState.java"
replace_once(
    player,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationWorldBounds;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    player,
    '''    private final boolean crouching;
    private final double yawDegrees;''',
    '''    private final boolean crouching;
    private final PreparationBarrierPolicy barrierPolicy;
    private final double yawDegrees;''',
    "private final PreparationBarrierPolicy barrierPolicy;",
)
replace_once(
    player,
    '''            boolean grounded,
            boolean crouching,
            double yawDegrees,''',
    '''            boolean grounded,
            boolean crouching,
            PreparationBarrierPolicy barrierPolicy,
            double yawDegrees,''',
    "            PreparationBarrierPolicy barrierPolicy,\n            double yawDegrees",
)
replace_once(
    player,
    '''        this.scene = Objects.requireNonNull(scene, "scene");
        this.position = Objects.requireNonNull(position, "position");
        if (!scene.region().contains(position)) {
            throw new IllegalArgumentException(
                    "preparation player position must remain inside the verified region");
        }''',
    '''        this.scene = Objects.requireNonNull(scene, "scene");
        this.position = Objects.requireNonNull(position, "position");
        this.barrierPolicy = Objects.requireNonNull(barrierPolicy, "barrierPolicy");
        boolean insideBounds =
                barrierPolicy == PreparationBarrierPolicy.OPEN
                        ? scene.worldBounds().contains(position)
                        : scene.region().contains(position);
        if (!insideBounds) {
            throw new IllegalArgumentException(
                    "preparation player position must remain inside the active verified bounds");
        }''',
    "boolean insideBounds =",
)
replace_once(
    player,
    '''                .hasPlayerClearance(position.x(), position.y(), position.z(), crouching)) {''',
    '''                .hasPlayerClearance(
                        position.x(), position.y(), position.z(), crouching, barrierPolicy)) {''',
    "position.x(), position.y(), position.z(), crouching, barrierPolicy",
)
replace_once(
    player,
    '''                true,
                false,
                normalizeYaw(verifiedScene.spawn().yawDegrees()),''',
    '''                true,
                false,
                PreparationBarrierPolicy.CLOSED,
                normalizeYaw(verifiedScene.spawn().yawDegrees()),''',
    "PreparationBarrierPolicy.CLOSED,\n                normalizeYaw",
)
replace_once(
    player,
    '''    public boolean crouching() {
        return crouching;
    }
''',
    '''    public boolean crouching() {
        return crouching;
    }

    public PreparationBarrierPolicy barrierPolicy() {
        return barrierPolicy;
    }

    public PreparationPlayerState withBarrierPolicy(PreparationBarrierPolicy nextPolicy) {
        PreparationBarrierPolicy requested = Objects.requireNonNull(nextPolicy, "nextPolicy");
        if (requested == barrierPolicy) {
            return this;
        }
        if (barrierPolicy == PreparationBarrierPolicy.OPEN) {
            throw new IllegalArgumentException(
                    "central barriers cannot close again during the local round");
        }
        return new PreparationPlayerState(
                scene,
                position,
                verticalVelocityMetresPerSecond,
                grounded,
                crouching,
                requested,
                yawDegrees,
                pitchDegrees);
    }
''',
    "public PreparationBarrierPolicy barrierPolicy()",
)
for old, new, marker in [
    (
        '''                authoritativeGrounded,
                authoritativeCrouching,
                normalizeYaw(authoritativeYawDegrees),''',
        '''                authoritativeGrounded,
                authoritativeCrouching,
                barrierPolicy,
                normalizeYaw(authoritativeYawDegrees),''',
        "authoritativeCrouching,\n                barrierPolicy,\n                normalizeYaw",
    ),
    (
        '''                nextGrounded,
                nextCrouching,
                yawDegrees,''',
        '''                nextGrounded,
                nextCrouching,
                barrierPolicy,
                yawDegrees,''',
        "nextCrouching,\n                barrierPolicy,\n                yawDegrees",
    ),
    (
        '''                grounded,
                nextCrouching,
                yawDegrees,''',
        '''                grounded,
                nextCrouching,
                barrierPolicy,
                yawDegrees,''',
        "grounded,\n                nextCrouching,\n                barrierPolicy",
    ),
    (
        '''                grounded,
                crouching,
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState withVerticalState''',
        '''                grounded,
                crouching,
                barrierPolicy,
                yawDegrees,
                pitchDegrees);
    }

    public PreparationPlayerState withVerticalState''',
        "crouching,\n                barrierPolicy,\n                yawDegrees,\n                pitchDegrees);\n    }\n\n    public PreparationPlayerState withVerticalState",
    ),
    (
        '''                nextGrounded,
                crouching,
                yawDegrees,''',
        '''                nextGrounded,
                crouching,
                barrierPolicy,
                yawDegrees,''',
        "nextGrounded,\n                crouching,\n                barrierPolicy",
    ),
    (
        '''                grounded,
                crouching,
                nextYaw,''',
        '''                grounded,
                crouching,
                barrierPolicy,
                nextYaw,''',
        "grounded,\n                crouching,\n                barrierPolicy,\n                nextYaw",
    ),
]:
    replace_once(player, old, new, marker)
replace_once(
    player,
    '''        PreparationRegion region = scene.region();
        return new MapVector3(
                clamp(addFinite(position.x(), deltaX), region.minimum().x(), region.maximum().x()),
                position.y(),
                clamp(addFinite(position.z(), deltaZ), region.minimum().z(), region.maximum().z()));''',
    '''        double requestedX = addFinite(position.x(), deltaX);
        double requestedZ = addFinite(position.z(), deltaZ);
        if (barrierPolicy == PreparationBarrierPolicy.OPEN) {
            PreparationWorldBounds bounds = scene.worldBounds();
            return new MapVector3(
                    bounds.clampX(requestedX), position.y(), bounds.clampZ(requestedZ));
        }
        PreparationRegion region = scene.region();
        return new MapVector3(
                clamp(requestedX, region.minimum().x(), region.maximum().x()),
                position.y(),
                clamp(requestedZ, region.minimum().z(), region.maximum().z()));''',
    "double requestedX = addFinite(position.x(), deltaX);",
)

collision = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationCollisionWorld.java"
replace_once(
    collision,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    collision,
    '''    public boolean hasPlayerClearance(MapVector3 position, boolean crouching) {
        MapVector3 point = Objects.requireNonNull(position, "position");
        return obstacleMap.hasPlayerClearance(point.x(), point.y(), point.z(), crouching)
                && hasBodyClearance(toVector(point));
    }

    public double limitUpwardMovement(MapVector3 current, double targetYMetres, boolean crouching) {
        MapVector3 point = Objects.requireNonNull(current, "current");
        return obstacleMap.limitUpwardMovement(
                point.x(), point.z(), point.y(), targetYMetres, crouching);
    }
''',
    '''    public boolean hasPlayerClearance(MapVector3 position, boolean crouching) {
        return hasPlayerClearance(position, crouching, PreparationBarrierPolicy.CLOSED);
    }

    public boolean hasPlayerClearance(
            MapVector3 position,
            boolean crouching,
            PreparationBarrierPolicy barrierPolicy) {
        MapVector3 point = Objects.requireNonNull(position, "position");
        PreparationBarrierPolicy policy =
                Objects.requireNonNull(barrierPolicy, "barrierPolicy");
        return obstacleMap.hasPlayerClearance(
                        point.x(), point.y(), point.z(), crouching, policy)
                && hasBodyClearance(toVector(point), policy);
    }

    public double limitUpwardMovement(MapVector3 current, double targetYMetres, boolean crouching) {
        return limitUpwardMovement(
                current, targetYMetres, crouching, PreparationBarrierPolicy.CLOSED);
    }

    public double limitUpwardMovement(
            MapVector3 current,
            double targetYMetres,
            boolean crouching,
            PreparationBarrierPolicy barrierPolicy) {
        MapVector3 point = Objects.requireNonNull(current, "current");
        return obstacleMap.limitUpwardMovement(
                point.x(),
                point.z(),
                point.y(),
                targetYMetres,
                crouching,
                Objects.requireNonNull(barrierPolicy, "barrierPolicy"));
    }
''',
    "PreparationBarrierPolicy barrierPolicy) {\n        MapVector3 point",
)
replace_once(
    collision,
    '''        return permitsHorizontal(current, target, true, false);''',
    '''        return permitsHorizontal(
                current, target, true, false, PreparationBarrierPolicy.CLOSED);''',
    "current, target, true, false, PreparationBarrierPolicy.CLOSED",
)
replace_once(
    collision,
    '''        return permitsHorizontal(current, target, requireGroundSupport, false);''',
    '''        return permitsHorizontal(
                current,
                target,
                requireGroundSupport,
                false,
                PreparationBarrierPolicy.CLOSED);''',
    "requireGroundSupport,\n                false,\n                PreparationBarrierPolicy.CLOSED",
)
replace_once(
    collision,
    '''            boolean requireGroundSupport,
            boolean crouching) {
        MapVector3 origin = Objects.requireNonNull(current, "current");''',
    '''            boolean requireGroundSupport,
            boolean crouching) {
        return permitsHorizontal(
                current,
                target,
                requireGroundSupport,
                crouching,
                PreparationBarrierPolicy.CLOSED);
    }

    public boolean permitsHorizontal(
            MapVector3 current,
            MapVector3 target,
            boolean requireGroundSupport,
            boolean crouching,
            PreparationBarrierPolicy barrierPolicy) {
        MapVector3 origin = Objects.requireNonNull(current, "current");
        PreparationBarrierPolicy policy =
                Objects.requireNonNull(barrierPolicy, "barrierPolicy");''',
    "boolean crouching,\n            PreparationBarrierPolicy barrierPolicy",
)
replace_once(
    collision,
    '''                destination.z(),
                crouching)) {''',
    '''                destination.z(),
                crouching,
                policy)) {''',
    "destination.z(),\n                crouching,\n                policy))",
)
replace_once(collision, "!hasBodyClearance(start) || !hasBodyClearance(end)", "!hasBodyClearance(start, policy) || !hasBodyClearance(end, policy)", "hasBodyClearance(start, policy)")
replace_once(collision, "rayMeetsObstacle(start, movement.normalize(), distance)", "rayMeetsObstacle(start, movement.normalize(), distance, policy)", "distance, policy)")
replace_once(collision, "!hasBodyClearance(sample)", "!hasBodyClearance(sample, policy)", "hasBodyClearance(sample, policy)")
replace_once(
    collision,
    '''    private boolean hasBodyClearance(Vector3f center) {
        CollisionResults results = new CollisionResults();
        graph.collideWith(new BoundingSphere(PLAYER_BODY_RADIUS_METRES, center), results);
        for (CollisionResult result : results) {
            if (!belongsToSupport(result.getGeometry())) {
                return false;
            }
        }
        return true;
    }

    private boolean rayMeetsObstacle(Vector3f start, Vector3f direction, float distance) {''',
    '''    private boolean hasBodyClearance(
            Vector3f center, PreparationBarrierPolicy barrierPolicy) {
        CollisionResults results = new CollisionResults();
        graph.collideWith(new BoundingSphere(PLAYER_BODY_RADIUS_METRES, center), results);
        for (CollisionResult result : results) {
            if (blocks(result.getGeometry(), barrierPolicy)) {
                return false;
            }
        }
        return true;
    }

    private boolean rayMeetsObstacle(
            Vector3f start,
            Vector3f direction,
            float distance,
            PreparationBarrierPolicy barrierPolicy) {''',
    "private boolean hasBodyClearance(\n            Vector3f center, PreparationBarrierPolicy",
)
replace_once(
    collision,
    '''            if (!belongsToSupport(result.getGeometry())
                    && result.getDistance() <= distance + COLLISION_EPSILON) {''',
    '''            if (blocks(result.getGeometry(), barrierPolicy)
                    && result.getDistance() <= distance + COLLISION_EPSILON) {''',
    "blocks(result.getGeometry(), barrierPolicy)",
)
replace_once(
    collision,
    '''    private static boolean belongsToSupport(Spatial spatial) {''',
    '''    private static boolean blocks(
            Spatial spatial, PreparationBarrierPolicy barrierPolicy) {
        return !belongsToSupport(spatial)
                && (barrierPolicy.blocksCentralBarriers() || !belongsToCentralBarrier(spatial));
    }

    private static boolean belongsToCentralBarrier(Spatial spatial) {
        Spatial current = spatial;
        while (current != null) {
            String name = current.getName();
            if (CENTRAL_WALL_X.equals(name) || CENTRAL_WALL_Z.equals(name)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean belongsToSupport(Spatial spatial) {''',
    "private static boolean belongsToCentralBarrier",
)

movement = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationMovementController.java"
replace_once(
    movement,
    '''                        moved.position(), vertical.heightMetres(), moved.crouching());''',
    '''                        moved.position(),
                        vertical.heightMetres(),
                        moved.crouching(),
                        moved.barrierPolicy());''',
    "moved.crouching(),\n                        moved.barrierPolicy())",
)
replace_once(
    movement,
    '''        if (!player.crouching() || !world.hasPlayerClearance(player.position(), false)) {''',
    '''        if (!player.crouching()
                || !world.hasPlayerClearance(
                        player.position(), false, player.barrierPolicy())) {''',
    "player.position(), false, player.barrierPolicy()",
)
replace_once(
    movement,
    '''                || !world.permitsHorizontal(player.position(), target, false, player.crouching())) {''',
    '''                || !world.permitsHorizontal(
                        player.position(),
                        target,
                        false,
                        player.crouching(),
                        player.barrierPolicy())) {''',
    "player.crouching(),\n                        player.barrierPolicy()))",
)
replace_once(
    movement,
    '''                        target.z(),
                        player.crouching())) {''',
    '''                        target.z(),
                        player.crouching(),
                        player.barrierPolicy())) {''',
    "target.z(),\n                        player.crouching(),\n                        player.barrierPolicy()))",
)

history = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistory.java"
replace_once(
    history,
    '''import java.util.ArrayDeque;
import java.util.Objects;''',
    '''import java.util.ArrayDeque;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    history,
    '''                        crouching,
                        jumpEdge,
                        player.yawDegrees(),''',
    '''                        crouching,
                        jumpEdge,
                        player.barrierPolicy(),
                        player.yawDegrees(),''',
    "jumpEdge,\n                        player.barrierPolicy(),",
)
replace_once(
    history,
    '''    public int pendingStepCount() {
        return pending.size();
    }
''',
    '''    public void clearPendingAtBarrierPolicyBoundary() {
        pending.clear();
        consumedJumpSequence = highestSubmittedSequence;
    }

    public int pendingStepCount() {
        return pending.size();
    }
''',
    "public void clearPendingAtBarrierPolicyBoundary()",
)
replace_once(
    history,
    '''        PreparationPlayerState oriented =
                state.withAuthoritativeState(''',
    '''        PreparationPlayerState oriented =
                state.withBarrierPolicy(step.barrierPolicy())
                        .withAuthoritativeState(''',
    "state.withBarrierPolicy(step.barrierPolicy())",
)
replace_once(
    history,
    '''            boolean crouching,
            boolean jumping,
            double yawDegrees,''',
    '''            boolean crouching,
            boolean jumping,
            PreparationBarrierPolicy barrierPolicy,
            double yawDegrees,''',
    "PreparationBarrierPolicy barrierPolicy,\n            double yawDegrees",
)
replace_once(
    history,
    '''            if (crouching && jumping) {
                throw new IllegalArgumentException("crouching and jumping are mutually exclusive");
            }
            if (!Double.isFinite(yawDegrees)''',
    '''            if (crouching && jumping) {
                throw new IllegalArgumentException("crouching and jumping are mutually exclusive");
            }
            Objects.requireNonNull(barrierPolicy, "barrierPolicy");
            if (!Double.isFinite(yawDegrees)''',
    "Objects.requireNonNull(barrierPolicy, \"barrierPolicy\")",
)

controller = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java"
replace_once(
    controller,
    '''    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {''',
    '''    public Optional<LobbyMatchPhaseSnapshot> currentMatchSnapshot() {
        requireOpen();
        ConnectedLobbySession session = connectedSession;
        return session == null ? Optional.empty() : Optional.of(session.currentMatchSnapshot());
    }

    public Optional<PreparationSpawnAssignment> currentPreparationSpawnAssignment() {''',
    "public Optional<LobbyMatchPhaseSnapshot> currentMatchSnapshot()",
)

client = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
replace_once(client, "import com.jme3.scene.Node;", "import com.jme3.scene.Node;\nimport com.jme3.scene.Spatial;", "import com.jme3.scene.Spatial;")
replace_once(
    client,
    '''import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    client,
    '''    private static final String INPUT_JUMP = "sunderfront-jump";
    private static final double PREPARATION_INPUT_INTERVAL_SECONDS = 0.05d;''',
    '''    private static final String INPUT_JUMP = "sunderfront-jump";
    private static final String CENTRAL_WALL_X_VISUAL = "CentralWallX";
    private static final String CENTRAL_WALL_Z_VISUAL = "CentralWallZ";
    private static final double PREPARATION_INPUT_INTERVAL_SECONDS = 0.05d;''',
    "CENTRAL_WALL_X_VISUAL",
)
replace_once(
    client,
    '''    private Node preparationWorld;
    private PlayerId preparationPlayerId;''',
    '''    private Node preparationWorld;
    private PreparationBarrierPolicy preparationBarrierPolicy = PreparationBarrierPolicy.CLOSED;
    private PlayerId preparationPlayerId;''',
    "private PreparationBarrierPolicy preparationBarrierPolicy",
)
replace_once(
    client,
    '''            advancePreparationMovementDiagnostics(timePerFrame);
            applyPreparationSnapshot();''',
    '''            advancePreparationMovementDiagnostics(timePerFrame);
            applyPreparationMatchPhase();
            applyPreparationSnapshot();''',
    "applyPreparationMatchPhase();",
)
replace_once(
    client,
    '''            preparationWorld = loadedWorld;
            preparationCollisionWorld = loadedCollisions;''',
    '''            preparationWorld = loadedWorld;
            preparationBarrierPolicy = PreparationBarrierPolicy.CLOSED;
            preparationCollisionWorld = loadedCollisions;''',
    "preparationBarrierPolicy = PreparationBarrierPolicy.CLOSED;\n            preparationCollisionWorld",
)
replace_once(
    client,
    '''        preparationCollisionWorld = null;
        preparationPlayerId = null;''',
    '''        preparationCollisionWorld = null;
        preparationBarrierPolicy = PreparationBarrierPolicy.CLOSED;
        preparationPlayerId = null;''',
    "preparationCollisionWorld = null;\n        preparationBarrierPolicy = PreparationBarrierPolicy.CLOSED;",
)
replace_once(
    client,
    '''    private void applyPreparationSnapshot() {''',
    '''    private void applyPreparationMatchPhase() {
        DirectConnectUiController controller = directConnectController;
        PreparationPlayerState current = preparationPlayerState;
        PreparationPredictionHistory history = preparationPredictionHistory;
        if (controller == null || current == null || history == null) {
            failPreparationSceneEntry();
            return;
        }
        Optional<LobbyMatchPhaseSnapshot> available = controller.currentMatchSnapshot();
        if (available.isEmpty()) {
            return;
        }
        LobbyMatchPhaseSnapshot snapshot = available.orElseThrow();
        if (snapshot.roundNumber() != preparationRoundNumber) {
            failPreparationSceneEntry();
            return;
        }
        if (snapshot.phase() != LobbyMatchPhase.OPEN_COMBAT
                || preparationBarrierPolicy == PreparationBarrierPolicy.OPEN) {
            return;
        }
        try {
            history.clearPendingAtBarrierPolicyBoundary();
            pendingPreparationJump = false;
            preparationBarrierPolicy = PreparationBarrierPolicy.OPEN;
            preparationPlayerState = current.withBarrierPolicy(preparationBarrierPolicy);
            detachCentralWallVisuals();
            PreparationCameraPlacement.apply(
                    cam, preparationPlayerState, preparationInput.crouching());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            failPreparationSceneEntry();
        }
    }

    private void detachCentralWallVisuals() {
        Node world = preparationWorld;
        if (world == null) {
            throw new IllegalStateException("preparation world is not attached");
        }
        detachNamedSpatial(world, CENTRAL_WALL_X_VISUAL);
        detachNamedSpatial(world, CENTRAL_WALL_Z_VISUAL);
    }

    private static void detachNamedSpatial(Spatial root, String name) {
        Spatial spatial = findNamedSpatial(root, name);
        if (spatial == null) {
            throw new IllegalStateException("verified preparation scene is missing " + name);
        }
        spatial.removeFromParent();
    }

    private static Spatial findNamedSpatial(Spatial current, String name) {
        if (name.equals(current.getName())) {
            return current;
        }
        if (current instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Spatial found = findNamedSpatial(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void applyPreparationSnapshot() {''',
    "private void applyPreparationMatchPhase()",
)

prediction_test = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistoryTest.java"
replace_once(
    prediction_test,
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;''',
    '''import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;''',
    "import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;",
)
replace_once(
    prediction_test,
    '''    @Test
    void acceptsAcknowledgementForSubmittedZeroInputWithoutPredictionSteps()''',
    '''    @Test
    void barrierPolicyBoundaryDropsTheOldTailAndUsesOpenWorldBounds()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        PreparationPlayerState closed =
                player().withAuthoritativeState(-1.4d, 0.5d, -14.0d, 0.0d, true, 0.0d, 0.0d);
        PreparationCollisionWorld collisions = collisions(closed);
        PreparationPredictionHistory history = new PreparationPredictionHistory();

        PreparationPlayerState clamped =
                history.predict(closed, collisions, 1L, 1.0d, 0.0d, true, 0.1d);
        history.markSubmitted(1L);
        history.clearPendingAtBarrierPolicyBoundary();
        PreparationPlayerState open = closed.withBarrierPolicy(PreparationBarrierPolicy.OPEN);
        PreparationPlayerState crossedSectorBound =
                history.predict(open, collisions, 2L, 1.0d, 0.0d, true, 0.1d);

        assertThat(clamped.position().x()).isEqualTo(-1.0d);
        assertThat(history.pendingStepCount()).isOne();
        assertThat(crossedSectorBound.position().x()).isGreaterThan(-1.0d);
        assertThat(crossedSectorBound.barrierPolicy()).isEqualTo(PreparationBarrierPolicy.OPEN);
        assertThrows(
                IllegalArgumentException.class,
                () -> crossedSectorBound.withBarrierPolicy(PreparationBarrierPolicy.CLOSED));
    }

    @Test
    void acceptsAcknowledgementForSubmittedZeroInputWithoutPredictionSteps()''',
    "void barrierPolicyBoundaryDropsTheOldTailAndUsesOpenWorldBounds()",
)
replace_once(
    prediction_test,
    '''                scene.supportMap(),
                new PreparationObstacleMap(boxes),
                scene.region(),''',
    '''                scene.supportMap(),
                new PreparationObstacleMap(boxes),
                scene.worldBounds(),
                scene.region(),''',
    "new PreparationObstacleMap(boxes),\n                scene.worldBounds(),",
)
