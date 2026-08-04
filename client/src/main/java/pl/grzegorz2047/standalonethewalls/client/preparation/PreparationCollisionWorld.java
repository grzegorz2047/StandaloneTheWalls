package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;

/** Point-motion collision queries backed by the verified, invisible collision GLB. */
public final class PreparationCollisionWorld {
    private static final String GROUND = "GroundCollision";
    private static final String CENTRAL_WALL_X = "CentralWallXCollision";
    private static final String CENTRAL_WALL_Z = "CentralWallZCollision";
    private static final float MAXIMUM_SUPPORT_DISTANCE = 0.75f;
    private static final float COLLISION_EPSILON = 0.0001f;

    private final Node graph;

    private PreparationCollisionWorld(Node graph) throws PreparationSceneGraphException {
        this.graph = Objects.requireNonNull(graph, "graph");
        requireNode(GROUND);
        requireNode(CENTRAL_WALL_X);
        requireNode(CENTRAL_WALL_Z);
    }

    public static PreparationCollisionWorld load(
            AssetManager assetManager, VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        return new PreparationCollisionWorld(
                PreparationSceneGraphLoader.loadCollision(assetManager, scene));
    }

    public boolean hasGroundSupport(MapVector3 position) {
        MapVector3 point = Objects.requireNonNull(position, "position");
        Ray ray = new Ray(toVector(point), new Vector3f(0.0f, -1.0f, 0.0f));
        ray.setLimit(MAXIMUM_SUPPORT_DISTANCE);
        CollisionResults results = new CollisionResults();
        graph.collideWith(ray, results);
        for (CollisionResult result : results) {
            if (belongsTo(result.getGeometry(), GROUND)
                    && result.getDistance() <= MAXIMUM_SUPPORT_DISTANCE + COLLISION_EPSILON) {
                return true;
            }
        }
        return false;
    }

    public boolean permitsHorizontal(MapVector3 current, MapVector3 target) {
        MapVector3 origin = Objects.requireNonNull(current, "current");
        MapVector3 destination = Objects.requireNonNull(target, "target");
        if (Double.compare(origin.y(), destination.y()) != 0) {
            throw new IllegalArgumentException("horizontal collision query must preserve height");
        }

        Vector3f start = toVector(origin);
        Vector3f end = toVector(destination);
        Vector3f movement = end.subtract(start);
        float distance = movement.length();
        if (distance <= COLLISION_EPSILON) {
            return hasGroundSupport(destination);
        }

        Ray ray = new Ray(start, movement.normalize());
        ray.setLimit(distance);
        CollisionResults results = new CollisionResults();
        graph.collideWith(ray, results);
        for (CollisionResult result : results) {
            if (!belongsTo(result.getGeometry(), GROUND)
                    && result.getDistance() <= distance + COLLISION_EPSILON) {
                return false;
            }
        }
        return hasGroundSupport(destination);
    }

    private void requireNode(String name) throws PreparationSceneGraphException {
        if (graph.getChild(name) == null) {
            throw new PreparationSceneGraphException(
                    "verified preparation collision graph is missing " + name);
        }
    }

    private static boolean belongsTo(Spatial spatial, String name) {
        Spatial current = spatial;
        while (current != null) {
            if (name.equals(current.getName())) {
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
