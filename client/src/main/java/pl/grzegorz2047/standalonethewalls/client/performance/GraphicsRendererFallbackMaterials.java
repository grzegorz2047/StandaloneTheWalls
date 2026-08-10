package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Objects;

/** Replaces reference-scene materials with the conservative built-in unshaded material. */
final class GraphicsRendererFallbackMaterials {
    private static final String FALLBACK_MATERIAL = "Common/MatDefs/Misc/Unshaded.j3md";

    private GraphicsRendererFallbackMaterials() {
        throw new AssertionError("No instances");
    }

    static void apply(AssetManager assetManager, Spatial spatial) {
        Objects.requireNonNull(assetManager, "assetManager");
        Objects.requireNonNull(spatial, "spatial");
        if (spatial instanceof Geometry geometry) {
            replaceMaterial(assetManager, geometry);
            return;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                apply(assetManager, child);
            }
        }
    }

    private static void replaceMaterial(AssetManager assetManager, Geometry geometry) {
        ColorRGBA color = materialColor(Objects.requireNonNull(geometry.getMaterial(), "material"));
        Material fallback = new Material(assetManager, FALLBACK_MATERIAL);
        fallback.setColor("Color", color);
        if (geometry.getQueueBucket() == RenderQueue.Bucket.Transparent || color.a < 1.0f) {
            fallback.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            fallback.getAdditionalRenderState().setDepthWrite(false);
        }
        geometry.setMaterial(fallback);
    }

    private static ColorRGBA materialColor(Material material) {
        ColorRGBA diffuse = colorParam(material, "Diffuse");
        if (diffuse != null) {
            return diffuse;
        }
        ColorRGBA color = colorParam(material, "Color");
        return color != null ? color : ColorRGBA.White.clone();
    }

    private static ColorRGBA colorParam(Material material, String name) {
        MatParam parameter = material.getParam(name);
        if (parameter == null || !(parameter.getValue() instanceof ColorRGBA color)) {
            return null;
        }
        return color.clone();
    }
}
