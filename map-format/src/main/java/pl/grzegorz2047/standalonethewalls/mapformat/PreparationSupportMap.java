package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/** Bounded deterministic support-height queries shared by client prediction and server movement. */
public final class PreparationSupportMap {
    public static final int MAXIMUM_BOXES = 64;
    public static final double PLAYER_CENTER_OFFSET_METRES = 0.5d;
    private static final double HEIGHT_EPSILON_METRES = 0.000001d;

    private final List<PreparationSupportBox> boxes;

    public PreparationSupportMap(List<PreparationSupportBox> boxes) {
        List<PreparationSupportBox> copied =
                new ArrayList<>(List.copyOf(Objects.requireNonNull(boxes, "boxes")));
        if (copied.isEmpty() || copied.size() > MAXIMUM_BOXES) {
            throw new IllegalArgumentException("support box count is outside [1, 64]");
        }
        Set<String> names = new HashSet<>();
        boolean hasGround = false;
        for (PreparationSupportBox box : copied) {
            PreparationSupportBox candidate = Objects.requireNonNull(box, "supportBox");
            if (!names.add(candidate.name())) {
                throw new IllegalArgumentException("support box names must be unique");
            }
            hasGround |= "GroundCollision".equals(candidate.name());
        }
        if (!hasGround) {
            throw new IllegalArgumentException("support map must contain GroundCollision");
        }
        copied.sort(Comparator.comparing(PreparationSupportBox::name));
        this.boxes = List.copyOf(copied);
    }

    public List<PreparationSupportBox> boxes() {
        return boxes;
    }

    public OptionalDouble highestPlayerCenterAtOrBelow(
            double xMetres, double zMetres, double maximumPlayerCenterYMetres) {
        if (!Double.isFinite(xMetres)
                || !Double.isFinite(zMetres)
                || !Double.isFinite(maximumPlayerCenterYMetres)) {
            throw new IllegalArgumentException("support query values must be finite");
        }
        double maximumSurfaceY =
                maximumPlayerCenterYMetres - PLAYER_CENTER_OFFSET_METRES;
        double selectedSurfaceY = Double.NEGATIVE_INFINITY;
        for (PreparationSupportBox box : boxes) {
            double top = box.topYMetres();
            if (box.containsHorizontal(xMetres, zMetres)
                    && top <= maximumSurfaceY + HEIGHT_EPSILON_METRES
                    && top > selectedSurfaceY) {
                selectedSurfaceY = top;
            }
        }
        return selectedSurfaceY == Double.NEGATIVE_INFINITY
                ? OptionalDouble.empty()
                : OptionalDouble.of(selectedSurfaceY + PLAYER_CENTER_OFFSET_METRES);
    }
}
