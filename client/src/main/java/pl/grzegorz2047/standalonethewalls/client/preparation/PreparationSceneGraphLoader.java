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

/** Converts already verified preparation GLB bytes into a detached jMonkeyEngine scene graph. */
public final class PreparationSceneGraphLoader {
    private PreparationSceneGraphLoader() {
        throw new AssertionError("No instances");
    }

    public static Node load(AssetManager assetManager, VerifiedPreparationScene scene)
            throws PreparationSceneGraphException {
        AssetManager manager = Objects.requireNonNull(assetManager, "assetManager");
        VerifiedPreparationScene verified = Objects.requireNonNull(scene, "scene");
        manager.registerLoader(GlbLoader.class, "glb");
        String digest = HexFormat.of().formatHex(verified.mapSha256());
        ModelKey key = new ModelKey("verified-preparation/" + digest + "/scene.glb");
        try (ByteArrayInputStream input = new ByteArrayInputStream(verified.sceneGlb())) {
            Spatial loaded = manager.loadAssetFromStream(key, input);
            if (loaded == null) {
                throw new PreparationSceneGraphException(
                        "verified preparation scene loader returned no spatial");
            }
            Node root = new Node("verified-preparation-" + verified.mapId());
            root.attachChild(loaded);
            root.updateModelBound();
            root.updateGeometricState();
            if (root.getQuantity() == 0) {
                throw new PreparationSceneGraphException(
                        "verified preparation scene graph contains no spatial");
            }
            return root;
        } catch (AssetLoadException | IllegalArgumentException | IOException exception) {
            throw new PreparationSceneGraphException(
                    "verified preparation scene could not be loaded", exception);
        }
    }
}
