package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import java.util.Objects;
import java.util.Optional;

/** Owns the active hit map and left-button gesture without depending on a renderer API. */
public final class UiPointerRouter {
    public static final int PRIMARY_BUTTON = 0;

    private final UiPointerGesture gesture = new UiPointerGesture();
    private UiHitMap hitMap = UiHitMap.empty();

    public void replaceHitMap(UiHitMap next) {
        hitMap = Objects.requireNonNull(next, "next");
        gesture.cancel();
    }

    public Optional<UiTargetId> hover(float x, float y) {
        return hitMap.targetAt(x, y).map(UiHitTarget::id);
    }

    public Optional<UiTargetId> button(
            int buttonIndex, boolean pressed, float x, float y) {
        if (buttonIndex != PRIMARY_BUTTON) {
            return Optional.empty();
        }
        if (pressed) {
            gesture.press(hitMap, x, y);
            return Optional.empty();
        }
        return gesture.release(hitMap, x, y);
    }

    public UiHitMap hitMap() {
        return hitMap;
    }

    public Optional<UiTargetId> pressedTarget() {
        return gesture.pressedTarget();
    }
}
