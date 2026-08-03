package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import java.util.Objects;

/** One immutable interactive region; disabled targets remain in layout but cannot be hit. */
public record UiHitTarget(UiTargetId id, UiRect bounds, boolean enabled) {
    public UiHitTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
    }

    public static UiHitTarget enabled(UiTargetId id, UiRect bounds) {
        return new UiHitTarget(id, bounds, true);
    }
}
