package pl.grzegorz2047.standalonethewalls.client.performance;

/** Deterministic render-scale math shared by the benchmark CLI and framebuffer path. */
final class GraphicsBenchmarkRenderScale {
    static final double DIRECT_RENDER_SCALE = 1.0d;

    private GraphicsBenchmarkRenderScale() {
        throw new AssertionError("No instances");
    }

    static boolean requiresOffscreenRendering(double renderScale) {
        requireScale(renderScale);
        return renderScale < DIRECT_RENDER_SCALE;
    }

    static Dimensions scaledDimensions(int displayWidth, int displayHeight, double renderScale) {
        if (displayWidth < 1 || displayHeight < 1) {
            throw new IllegalArgumentException("display dimensions must be positive");
        }
        requireScale(renderScale);
        long scaledWidth = Math.max(1L, Math.round(displayWidth * renderScale));
        long scaledHeight = Math.max(1L, Math.round(displayHeight * renderScale));
        if (scaledWidth > Integer.MAX_VALUE || scaledHeight > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("scaled dimensions exceed integer bounds");
        }
        return new Dimensions(Math.toIntExact(scaledWidth), Math.toIntExact(scaledHeight));
    }

    static void requireScale(double renderScale) {
        if (!Double.isFinite(renderScale)
                || renderScale <= 0.0d
                || renderScale > DIRECT_RENDER_SCALE) {
            throw new IllegalArgumentException("render scale is outside the bounded range");
        }
    }

    record Dimensions(int width, int height) {
        Dimensions {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("scaled dimensions must be positive");
            }
        }
    }
}
