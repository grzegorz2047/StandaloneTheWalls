package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;

/** Mutable renderer-thread latch for explicit preparation input capture. */
public final class PreparationInputState {
    public enum Direction {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT
    }

    private boolean captured;
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean sprinting;
    private boolean crouching;

    public boolean capture() {
        if (captured) {
            return false;
        }
        captured = true;
        return true;
    }

    public boolean release() {
        if (!captured) {
            clearDirections();
            return false;
        }
        captured = false;
        clearDirections();
        return true;
    }

    public boolean captured() {
        return captured;
    }

    public void set(Direction direction, boolean pressed) {
        Direction requested = Objects.requireNonNull(direction, "direction");
        if (!captured) {
            return;
        }
        switch (requested) {
            case FORWARD -> forward = pressed;
            case BACKWARD -> backward = pressed;
            case LEFT -> left = pressed;
            case RIGHT -> right = pressed;
        }
    }

    public void setSprinting(boolean pressed) {
        if (captured) {
            sprinting = pressed;
        }
    }

    public void setCrouching(boolean pressed) {
        if (captured) {
            crouching = pressed;
        }
    }

    public boolean sprinting() {
        return sprinting && !crouching;
    }

    public boolean crouching() {
        return crouching;
    }

    public double forwardAxis() {
        return axis(forward, backward);
    }

    public double rightAxis() {
        return axis(right, left);
    }

    private void clearDirections() {
        forward = false;
        backward = false;
        left = false;
        right = false;
        sprinting = false;
        crouching = false;
    }

    private static double axis(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0d;
        }
        return positive ? 1.0d : -1.0d;
    }
}
