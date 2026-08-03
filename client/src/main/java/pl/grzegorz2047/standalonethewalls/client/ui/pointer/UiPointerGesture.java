package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import java.util.Objects;
import java.util.Optional;

/** Renderer-owned pointer gesture state that activates only a matching press and release. */
public final class UiPointerGesture {
    private UiTargetId pressedTarget;

    public Optional<UiTargetId> press(UiHitMap hitMap, float x, float y) {
        Objects.requireNonNull(hitMap, "hitMap");
        pressedTarget = hitMap.targetAt(x, y).map(UiHitTarget::id).orElse(null);
        return pressedTarget();
    }

    public Optional<UiTargetId> release(UiHitMap hitMap, float x, float y) {
        Objects.requireNonNull(hitMap, "hitMap");
        UiTargetId captured = pressedTarget;
        pressedTarget = null;
        if (captured == null) {
            return Optional.empty();
        }
        return hitMap.targetAt(x, y)
                .map(UiHitTarget::id)
                .filter(captured::equals);
    }

    public Optional<UiTargetId> pressedTarget() {
        return Optional.ofNullable(pressedTarget);
    }

    public void cancel() {
        pressedTarget = null;
    }
}
