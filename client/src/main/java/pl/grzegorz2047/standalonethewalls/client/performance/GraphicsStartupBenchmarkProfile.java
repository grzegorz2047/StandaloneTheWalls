package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.Objects;

/** Fixed short benchmark profile used only when startup quality state must be refreshed. */
final class GraphicsStartupBenchmarkProfile {
    static final GraphicsQualityPreset MEASURED_PRESET = GraphicsQualityPreset.MEDIUM;
    static final int WIDTH = 1280;
    static final int HEIGHT = 720;
    static final double RENDER_SCALE = GraphicsBenchmarkRenderScale.DIRECT_RENDER_SCALE;
    static final int WARM_UP_FRAMES = 120;
    static final int MEASUREMENT_FRAMES = 240;

    private GraphicsStartupBenchmarkProfile() {
        throw new AssertionError("No instances");
    }

    static GraphicsBenchmarkSession.Config config(
            String repositoryCommit, GraphicsBenchmarkCompatibilityKey compatibilityKey) {
        return new GraphicsBenchmarkSession.Config(
                Objects.requireNonNull(repositoryCommit, "repositoryCommit"),
                Objects.requireNonNull(compatibilityKey, "compatibilityKey"),
                MEASURED_PRESET,
                WIDTH,
                HEIGHT,
                RENDER_SCALE,
                WARM_UP_FRAMES,
                MEASUREMENT_FRAMES);
    }
}
