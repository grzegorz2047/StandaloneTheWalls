package pl.grzegorz2047.standalonethewalls.client.ui.pointer;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable ordered hit map; the last matching target is treated as visually topmost. */
public final class UiHitMap {
    private static final UiHitMap EMPTY = new UiHitMap(List.of());

    private final List<UiHitTarget> targets;

    public UiHitMap(List<UiHitTarget> targets) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        Set<UiTargetId> identifiers = new HashSet<>();
        for (UiHitTarget target : this.targets) {
            Objects.requireNonNull(target, "target");
            if (!identifiers.add(target.id())) {
                throw new IllegalArgumentException("UI hit map contains a duplicate target id");
            }
        }
    }

    public static UiHitMap empty() {
        return EMPTY;
    }

    public List<UiHitTarget> targets() {
        return targets;
    }

    public Optional<UiHitTarget> targetAt(float x, float y) {
        ListIterator<UiHitTarget> iterator = targets.listIterator(targets.size());
        while (iterator.hasPrevious()) {
            UiHitTarget target = iterator.previous();
            if (target.enabled() && target.bounds().contains(x, y)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    public Optional<UiHitTarget> target(UiTargetId id) {
        Objects.requireNonNull(id, "id");
        return targets.stream().filter(target -> target.id().equals(id)).findFirst();
    }
}
