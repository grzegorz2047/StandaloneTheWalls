package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Reuses jMonkeyEngine primitives to present interpolated remote preparation players. */
public final class PreparationRemotePlayerRenderer implements AutoCloseable {
    private static final float HALF_WIDTH = 0.35f;
    private static final float HALF_HEIGHT = 0.9f;
    private static final float HALF_DEPTH = 0.35f;

    private final Node root = new Node("preparation-remote-players");
    private final Material material;
    private final Map<PlayerId, Geometry> geometries = new HashMap<>();

    public PreparationRemotePlayerRenderer(AssetManager assetManager) {
        material =
                new Material(
                        Objects.requireNonNull(assetManager, "assetManager"),
                        "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.25f, 0.82f, 0.95f, 1.0f));
    }

    public void attachTo(Node parent) {
        Objects.requireNonNull(parent, "parent").attachChild(root);
    }

    public void apply(List<PreparationRemotePlayerPose> players) {
        List<PreparationRemotePlayerPose> presented =
                List.copyOf(Objects.requireNonNull(players, "players"));
        Set<PlayerId> retained = new HashSet<>();
        for (PreparationRemotePlayerPose player : presented) {
            PreparationRemotePlayerPose pose = Objects.requireNonNull(player, "player");
            if (!retained.add(pose.playerId())) {
                throw new IllegalArgumentException("remote player poses contain a duplicate player");
            }
            Geometry geometry = geometries.computeIfAbsent(pose.playerId(), this::createGeometry);
            geometry.setLocalTranslation(
                    new Vector3f(
                            (float) pose.xMetres(),
                            (float) pose.yMetres() + HALF_HEIGHT,
                            (float) pose.zMetres()));
            geometry.setLocalRotation(
                    new Quaternion()
                            .fromAngleAxis(
                                    (float) Math.toRadians(-pose.yawDegrees()), Vector3f.UNIT_Y));
        }
        geometries
                .entrySet()
                .removeIf(
                        entry -> {
                            if (retained.contains(entry.getKey())) {
                                return false;
                            }
                            entry.getValue().removeFromParent();
                            return true;
                        });
    }

    public int renderedPlayerCount() {
        return geometries.size();
    }

    @Override
    public void close() {
        for (Geometry geometry : geometries.values()) {
            geometry.removeFromParent();
        }
        geometries.clear();
        root.removeFromParent();
    }

    private Geometry createGeometry(PlayerId playerId) {
        Geometry geometry =
                new Geometry(
                        "preparation-player-" + playerId.value(),
                        new Box(HALF_WIDTH, HALF_HEIGHT, HALF_DEPTH));
        geometry.setMaterial(material);
        root.attachChild(geometry);
        return geometry;
    }
}
