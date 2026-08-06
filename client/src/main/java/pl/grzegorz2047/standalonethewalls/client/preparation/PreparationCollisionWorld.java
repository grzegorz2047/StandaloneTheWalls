package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingSphere;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;
import java.util.OptionalDouble;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationBarrierPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;

/** Bounded player-body collision queries backed by the verified invisible collision GLB. */
public final class PreparationCollisionWorld {
    public static final float PLAYER_BODY_RADIUS_METRES =
            (float) PreparationObstacleMap.PLAYER_BODY_RADIUS_METRES;

    private static final String GROUND = "GroundCollision";
    private static final String SUPPORT_SUFFIX = "SupportCollision";
    private static final String CENTRAL_WALL_X = "CentralWallXCollision";
    private static final String CENTRAL_WALL_Z = "CentralWallZCollision";
    private static final double MAXIMUM_SUPPORT_DISTANCE_METRES = 0.75d;
    private static final float MAXIMUM_BODY_SAMPLE_SPACING_METRES = 0.05f;
    private static final float COLLISION_EPSILON = 0.0001f;

    private final Node graph;
    private final PreparationSupportMap supportMap;
    private final PreparationObstacleMap obstacleMap;

    private PreparationCollisionWorld(
            Node graph, PreparationSupportMap supportMap, PreparationObstacleMap obstacleMap)
            throws PreparationSceneGraphException {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.supportMap = Objects.requireNonNull(supportMap, "supportMap");
        this.obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
        requireNode(GROUND);
        requireNode(CENTRAL_WALL_X);
        requireNode(CENTRAL_WALL_Z);
    }

    public static PreparationCollisionWorld load(
            AssetManager assetManager, VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        VerifiedPreparationScene verifiedScene = Objects.requireNonNull(scene, "scene");
        return new PreparationCollisionWorld(
                PreparationSceneGraphLoader.loadCollision(assetManager, verifiedScene),
                verifiedScene.supportMap(),
                verifiedScene.obstacleMap());
    }

    public boolean hasGroundSupport(MapVector3 position) {
        MapVector3 point = Objects.requireNonNull(position, "position");
        OptionalDouble support =
                supportMap.highestPlayerCenterAtOrBelow(
                        point.x(), point.z(), point.y() + MAXIMUM_SUPPORT_DISTANCE_METRES);
        if (support.isEmpty()) {
            return false;
        }
        double distance = point.y() - support.orElseThrow();
        return distance >= -COLLISION_EPSILON
                && distance <= MAXIMUM_SUPPORT_DISTANCE_METRES + COLLISION_EPSILON;
    }

    public boolean hasPlayerClearance(MapVector3 position, boolean crouching) {
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

    public boolean permitsHorizontal(MapVector3 current, MapVector3 target) {
        return permitsHorizontal(
                current, target, true, false, PreparationBarrierPolicy.CLOSED);
    }

    public boolean permitsHorizontal(
            MapVector3 current, MapVector3 target, boolean requireGroundSupport) {
        return permitsHorizontal(
                current,
                target,
                requireGroundSupport,
                false,
                PreparationBarrierPolicy.CLOSED);
    }

    public boolean permitsHorizontal(
            MapVector3 current,
            MapVector3 target,
            boolean requireGroundSupport,
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
        MapVector3 destination = Objects.requireNonNull(target, "target");
        PreparationBarrierPolicy policy =
                Objects.requireNonNull(barrierPolicy, "barrierPolicy");
        if (Double.compare(origin.y(), destination.y()) != 0) {
            throw new IllegalArgumentException("horizontal collision query must preserve height");
        }
        if (!obstacleMap.permitsMovement(
                origin.x(),
                origin.y(),
                origin.z(),
                destination.x(),
                destination.y(),
                destination.z(),
                crouching,
                policy)) {
            return false;
        }

        Vector3f start = toVector(origin);
        Vector3f end = toVector(destination);
        if (!hasBodyClearance(start, policy) || !hasBodyClearance(end, policy)) {
            return false;
        }

        Vector3f movement = end.subtract(start);
        float distance = movement.length();
        if (distance <= COLLISION_EPSILON) {
            return !requireGroundSupport || hasGroundSupport(destination);
        }

        if (rayMeetsObstacle(start, movement.normalize(), distance, policy)) {
            return false;
        }

        int samples = Math.max(1, (int) Math.ceil(distance / MAXIMUM_BODY_SAMPLE_SPACING_METRES));
        for (int index = 1; index < samples; index++) {
            float fraction = (float) index / samples;
            Vector3f sample = start.add(movement.mult(fraction));
            if (!hasBodyClearance(sample, policy)) {
                return false;
            }
        }
        return !requireGroundSupport || hasGroundSupport(destination);
    }

    private boolean hasBodyClearance(
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
            PreparationBarrierPolicy barrierPolicy) {
        Ray ray = new Ray(start, direction);
        ray.setLimit(distance);
        CollisionResults results = new CollisionResults();
        graph.collideWith(ray, results);
        for (CollisionResult result : results) {
            if (blocks(result.getGeometry(), barrierPolicy)
                    && result.getDistance() <= distance + COLLISION_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private void requireNode(String name) throws PreparationSceneGraphException {
        if (graph.getChild(name) == null) {
            throw new PreparationSceneGraphException(
                    "verified preparation collision graph is missing " + name);
        }
    }

    private static boolean blocks(
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

    private static boolean belongsToSupport(Spatial spatial) {
        Spatial current = spatial;
        while (current != null) {
            String name = current.getName();
            if (GROUND.equals(name) || (name != null && name.endsWith(SUPPORT_SUFFIX))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static Vector3f toVector(MapVector3 value) {
        return new Vector3f((float) value.x(), (float) value.y(), (float) value.z());
    }
}
