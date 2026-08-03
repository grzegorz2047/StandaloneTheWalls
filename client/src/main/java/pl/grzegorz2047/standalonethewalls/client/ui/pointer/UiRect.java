package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

/** Finite bottom-left-origin rectangle with inclusive lower and exclusive upper bounds. */
public record UiRect(float left, float bottom, float width, float height) {
    public UiRect {
        requireFinite(left, "left");
        requireFinite(bottom, "bottom");
        requireFinite(width, "width");
        requireFinite(height, "height");
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("UI rectangle dimensions must be positive");
        }
        if (!Double.isFinite((double) left + width)
                || !Double.isFinite((double) bottom + height)) {
            throw new IllegalArgumentException("UI rectangle bounds must be finite");
        }
    }

    public boolean contains(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return false;
        }
        return x >= left
                && y >= bottom
                && (double) x < (double) left + width
                && (double) y < (double) bottom + height;
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
