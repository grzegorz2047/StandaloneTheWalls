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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

/** Reuses jMonkeyEngine primitives to present remote authoritative preparation players. */
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

    public void apply(PreparationWorldSnapshot snapshot, PlayerId localPlayerId) {
        PreparationWorldSnapshot authoritative = Objects.requireNonNull(snapshot, "snapshot");
        PlayerId local = Objects.requireNonNull(localPlayerId, "localPlayerId");
        Set<PlayerId> retained = new HashSet<>();
        for (PreparationPlayerSnapshot player : authoritative.players()) {
            if (player.playerId().equals(local)) {
                continue;
            }
            retained.add(player.playerId());
            Geometry geometry = geometries.computeIfAbsent(player.playerId(), this::createGeometry);
            geometry.setLocalTranslation(
                    new Vector3f(
                            (float) player.xMetres(),
                            (float) player.yMetres() + HALF_HEIGHT,
                            (float) player.zMetres()));
            geometry.setLocalRotation(
                    new Quaternion()
                            .fromAngleAxis(
                                    (float) Math.toRadians(-player.yawDegrees()), Vector3f.UNIT_Y));
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
