package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.shader.VarType;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphicsSceneTextureQualityTest {
    @Test
    void presetsApplyExactAnisotropyWithoutChangingOtherTextureState() {
        for (GraphicsQualityPreset preset : GraphicsQualityPreset.values()) {
            Texture2D texture = new Texture2D();
            texture.setMinFilter(Texture.MinFilter.Trilinear);
            texture.setMagFilter(Texture.MagFilter.Nearest);
            texture.setWrap(Texture.WrapMode.Repeat);
            Node root = new Node("root");
            root.attachChild(geometry("geometry", material(texture)));

            int updated =
                    GraphicsSceneTextureQuality.apply(
                            root,
                            preset.anisotropy(),
                            identitySet(),
                            identitySet());

            assertThat(updated).isOne();
            assertThat(texture.getAnisotropicFilter()).isEqualTo(preset.anisotropy());
            assertThat(texture.getMinFilter()).isEqualTo(Texture.MinFilter.Trilinear);
            assertThat(texture.getMagFilter()).isEqualTo(Texture.MagFilter.Nearest);
            assertThat(texture.getWrap(Texture.WrapAxis.S)).isEqualTo(Texture.WrapMode.Repeat);
            assertThat(texture.getWrap(Texture.WrapAxis.T)).isEqualTo(Texture.WrapMode.Repeat);
        }
    }

    @Test
    void sharedTextureIsUpdatedOnceAndRemainsShared() {
        Texture2D shared = new Texture2D();
        Material firstMaterial = material(shared);
        Material secondMaterial = material(shared);
        Geometry first = geometry("first", firstMaterial);
        Geometry second = geometry("second", secondMaterial);
        Node root = new Node("root");
        root.attachChild(first);
        root.attachChild(second);
        Set<Geometry> processedGeometries = identitySet();
        Set<Texture> processedTextures = identitySet();

        int firstPass =
                GraphicsSceneTextureQuality.apply(
                        root,
                        GraphicsQualityPreset.HIGH.anisotropy(),
                        processedGeometries,
                        processedTextures);
        int secondPass =
                GraphicsSceneTextureQuality.apply(
                        root,
                        GraphicsQualityPreset.HIGH.anisotropy(),
                        processedGeometries,
                        processedTextures);

        assertThat(firstPass).isOne();
        assertThat(secondPass).isZero();
        assertThat(processedGeometries).hasSize(2);
        assertThat(processedTextures).containsExactly(shared);
        assertThat(firstMaterial.getParam("ColorMap").getValue()).isSameAs(shared);
        assertThat(secondMaterial.getParam("ColorMap").getValue()).isSameAs(shared);
    }

    @Test
    void nonTextureParametersAreIgnored() {
        MaterialDef definition = new MaterialDef(null, "non-texture");
        definition.addMaterialParam(VarType.Float, "Value", null);
        Material material = new Material(definition);
        material.setFloat("Value", 0.5f);
        Node root = new Node("root");
        root.attachChild(geometry("geometry", material));

        int updated =
                GraphicsSceneTextureQuality.apply(
                        root,
                        GraphicsQualityPreset.LOW.anisotropy(),
                        identitySet(),
                        identitySet());

        assertThat(updated).isZero();
        assertThat(material.getParam("Value").getValue()).isEqualTo(0.5f);
    }

    private static Material material(Texture texture) {
        MaterialDef definition = new MaterialDef(null, "texture-test");
        definition.addMaterialParamTexture(
                VarType.Texture2D, "ColorMap", ColorSpace.Linear, null);
        Material material = new Material(definition);
        material.setTexture("ColorMap", texture);
        return material;
    }

    private static Geometry geometry(String name, Material material) {
        Geometry geometry = new Geometry(name, new Box(1.0f, 1.0f, 1.0f));
        geometry.setMaterial(material);
        return geometry;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
