package pl.grzegorz2047.standalonethewalls.client.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.shader.VarType;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class GraphicsRuntimeTextureQualityStateTest {
    @Test
    void discoversLateRootGeometryWithoutMutatingGuiTextures()
            throws InterruptedException, ExecutionException, TimeoutException {
        GraphicsRuntimeTextureQualityState state =
                new GraphicsRuntimeTextureQualityState(GraphicsQualityPreset.MEDIUM);
        Texture2D rootTexture = new Texture2D();
        Texture2D guiTexture = new Texture2D();
        TestApplication application = new TestApplication(state, rootTexture, guiTexture);

        Snapshot snapshot = run(application, application.snapshot);

        assertThat(snapshot.rootAnisotropy()).isEqualTo(GraphicsQualityPreset.MEDIUM.anisotropy());
        assertThat(snapshot.guiAnisotropy()).isZero();
        assertThat(snapshot.processedGeometries()).isOne();
        assertThat(snapshot.processedTextures()).isOne();
    }

    private static Snapshot run(TestApplication application, CompletableFuture<Snapshot> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        AppSettings settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        application.setSettings(settings);
        try {
            application.start(JmeContext.Type.Headless, true);
            return result.get(Duration.ofSeconds(5).toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            if (application.getContext() != null) {
                application.stop(true);
            }
        }
    }

    private static final class TestApplication extends SimpleApplication {
        private final GraphicsRuntimeTextureQualityState state;
        private final Texture2D rootTexture;
        private final Texture2D guiTexture;
        private final CompletableFuture<Snapshot> snapshot = new CompletableFuture<>();
        private boolean rootAttached;

        private TestApplication(
                GraphicsRuntimeTextureQualityState state,
                Texture2D rootTexture,
                Texture2D guiTexture) {
            super(state);
            this.state = state;
            this.rootTexture = rootTexture;
            this.guiTexture = guiTexture;
        }

        @Override
        public void simpleInitApp() {
            guiNode.attachChild(geometry("gui", material(guiTexture)));
        }

        @Override
        public void simpleUpdate(float timePerFrame) {
            if (!rootAttached) {
                rootNode.attachChild(geometry("root", material(rootTexture)));
                rootAttached = true;
                return;
            }
            if (rootTexture.getAnisotropicFilter() == GraphicsQualityPreset.MEDIUM.anisotropy()) {
                snapshot.complete(
                        new Snapshot(
                                rootTexture.getAnisotropicFilter(),
                                guiTexture.getAnisotropicFilter(),
                                state.processedGeometryCount(),
                                state.processedTextureCount()));
            }
        }
    }

    private static Material material(Texture2D texture) {
        MaterialDef definition = new MaterialDef(null, "runtime-texture-test");
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

    private record Snapshot(
            int rootAnisotropy, int guiAnisotropy, int processedGeometries, int processedTextures) {}
}
