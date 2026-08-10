package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import java.util.Objects;
import java.util.Set;

/** Applies texture-only quality fields to newly discovered scene geometry. */
final class GraphicsSceneTextureQuality {
    private GraphicsSceneTextureQuality() {
        throw new AssertionError("No instances");
    }

    static int apply(
            Spatial spatial,
            int anisotropy,
            Set<Geometry> processedGeometries,
            Set<Texture> processedTextures) {
        Objects.requireNonNull(spatial, "spatial");
        Objects.requireNonNull(processedGeometries, "processedGeometries");
        Objects.requireNonNull(processedTextures, "processedTextures");
        if (anisotropy < 1) {
            throw new IllegalArgumentException("anisotropy must be positive");
        }
        if (spatial instanceof Geometry geometry) {
            return applyGeometry(geometry, anisotropy, processedGeometries, processedTextures);
        }
        if (!(spatial instanceof Node node)) {
            return 0;
        }
        int updated = 0;
        for (Spatial child : node.getChildren()) {
            updated =
                    Math.addExact(
                            updated,
                            apply(child, anisotropy, processedGeometries, processedTextures));
        }
        return updated;
    }

    private static int applyGeometry(
            Geometry geometry,
            int anisotropy,
            Set<Geometry> processedGeometries,
            Set<Texture> processedTextures) {
        if (!processedGeometries.add(geometry)) {
            return 0;
        }
        Material material = geometry.getMaterial();
        if (material == null) {
            return 0;
        }
        int updated = 0;
        for (MatParam parameter : material.getParams()) {
            if (!parameter.getVarType().isTextureType()
                    || !(parameter.getValue() instanceof Texture texture)
                    || !processedTextures.add(texture)) {
                continue;
            }
            texture.setAnisotropicFilter(anisotropy);
            updated = Math.addExact(updated, 1);
        }
        return updated;
    }
}
