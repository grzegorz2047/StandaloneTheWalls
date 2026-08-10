package pl.grzegorz2047.standalonethewalls.client.performance;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Applies the selected preset's texture filtering to newly attached 3D scene geometry. */
public final class GraphicsRuntimeTextureQualityState extends BaseAppState {
    private final int anisotropy;
    private final Set<Geometry> processedGeometries =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Texture> processedTextures =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private SimpleApplication application;

    public GraphicsRuntimeTextureQualityState(GraphicsQualityPreset preset) {
        anisotropy = Objects.requireNonNull(preset, "preset").anisotropy();
    }

    @Override
    protected void initialize(Application application) {
        if (!(application instanceof SimpleApplication simpleApplication)) {
            throw new IllegalArgumentException("runtime texture quality requires SimpleApplication");
        }
        if (this.application != null) {
            throw new IllegalStateException("runtime texture quality state is already initialized");
        }
        this.application = simpleApplication;
        applyToRootScene();
    }

    @Override
    public void update(float timePerFrame) {
        applyToRootScene();
    }

    @Override
    protected void cleanup(Application application) {
        processedGeometries.clear();
        processedTextures.clear();
        this.application = null;
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    int processedGeometryCount() {
        return processedGeometries.size();
    }

    int processedTextureCount() {
        return processedTextures.size();
    }

    private void applyToRootScene() {
        SimpleApplication currentApplication = application;
        if (currentApplication == null) {
            return;
        }
        GraphicsSceneTextureQuality.apply(
                currentApplication.getRootNode(),
                anisotropy,
                processedGeometries,
                processedTextures);
    }
}
