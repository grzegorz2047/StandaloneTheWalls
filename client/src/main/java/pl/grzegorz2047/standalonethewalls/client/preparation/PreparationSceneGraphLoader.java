package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.gltf.GlbLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Objects;

/** Converts already verified preparation GLB bytes into detached jMonkeyEngine scene graphs. */
public final class PreparationSceneGraphLoader {
    private PreparationSceneGraphLoader() {
        throw new AssertionError("No instances");
    }

    public static Node load(AssetManager assetManager, VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        VerifiedPreparationScene verified = Objects.requireNonNull(scene, "scene");
        return loadGlb(
                assetManager,
                verified,
                verified.sceneGlb(),
                "scene.glb",
                "verified-preparation-" + verified.mapId());
    }

    public static Node loadCollision(AssetManager assetManager, VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        VerifiedPreparationScene verified = Objects.requireNonNull(scene, "scene");
        return loadGlb(
                assetManager,
                verified,
                verified.collisionGlb(),
                "collision.glb",
                "verified-preparation-collision-" + verified.mapId());
    }

    private static Node loadGlb(
            AssetManager assetManager,
            VerifiedPreparationScene verified,
            byte[] glb,
            String memberName,
            String rootName)
            throws PreparationSceneGraphException {
        AssetManager manager = Objects.requireNonNull(assetManager, "assetManager");
        manager.registerLoader(GlbLoader.class, "glb");
        String digest = HexFormat.of().formatHex(verified.mapSha256());
        ModelKey key = new ModelKey("verified-preparation/" + digest + "/" + memberName);
        try (ByteArrayInputStream input = new ByteArrayInputStream(glb)) {
            Spatial loaded = manager.loadAssetFromStream(key, input);
            if (loaded == null) {
                throw new PreparationSceneGraphException(
                        "verified preparation " + memberName + " loader returned no spatial");
            }
            Node root = new Node(rootName);
            root.attachChild(loaded);
            root.updateModelBound();
            root.updateGeometricState();
            if (root.getQuantity() == 0) {
                throw new PreparationSceneGraphException(
                        "verified preparation " + memberName + " graph contains no spatial");
            }
            return root;
        } catch (AssetLoadException | IllegalArgumentException | IOException exception) {
            throw new PreparationSceneGraphException(
                    "verified preparation " + memberName + " could not be loaded", exception);
        }
    }
}
