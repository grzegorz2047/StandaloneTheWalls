package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded deterministic obstacle queries shared by map validation and server movement. */
public final class PreparationObstacleMap {
    public static final int MAXIMUM_BOXES = 64;
    public static final double PLAYER_BODY_RADIUS_METRES = 0.35d;
    private static final double COLLISION_EPSILON_METRES = 0.000001d;

    private final List<PreparationObstacleBox> boxes;

    public PreparationObstacleMap(List<PreparationObstacleBox> boxes) {
        List<PreparationObstacleBox> copied =
                new ArrayList<>(List.copyOf(Objects.requireNonNull(boxes, "boxes")));
        if (copied.size() > MAXIMUM_BOXES) {
            throw new IllegalArgumentException("obstacle box count exceeds 64");
        }
        Set<String> names = new HashSet<>();
        for (PreparationObstacleBox box : copied) {
            PreparationObstacleBox candidate = Objects.requireNonNull(box, "obstacleBox");
            if (!names.add(candidate.name())) {
                throw new IllegalArgumentException("obstacle box names must be unique");
            }
        }
        copied.sort(Comparator.comparing(PreparationObstacleBox::name));
        this.boxes = List.copyOf(copied);
    }

    public static PreparationObstacleMap empty() {
        return new PreparationObstacleMap(List.of());
    }

    public List<PreparationObstacleBox> boxes() {
        return boxes;
    }

    public boolean overlapsPlayerBody(double xMetres, double yMetres, double zMetres) {
        requireFinite(xMetres, "xMetres");
        requireFinite(yMetres, "yMetres");
        requireFinite(zMetres, "zMetres");
        for (PreparationObstacleBox box : boxes) {
            if (overlapsVertical(box, yMetres, yMetres)
                    && pointDistanceSquaredToHorizontalBox(xMetres, zMetres, box)
                            <= radiusSquaredWithEpsilon()) {
                return true;
            }
        }
        return false;
    }

    public boolean permitsMovement(
            double startXMetres,
            double startYMetres,
            double startZMetres,
            double targetXMetres,
            double targetYMetres,
            double targetZMetres) {
        requireFinite(startXMetres, "startXMetres");
        requireFinite(startYMetres, "startYMetres");
        requireFinite(startZMetres, "startZMetres");
        requireFinite(targetXMetres, "targetXMetres");
        requireFinite(targetYMetres, "targetYMetres");
        requireFinite(targetZMetres, "targetZMetres");
        for (PreparationObstacleBox box : boxes) {
            if (overlapsVertical(box, startYMetres, targetYMetres)
                    && sweptCircleIntersects(
                            startXMetres,
                            startZMetres,
                            targetXMetres,
                            targetZMetres,
                            box)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sweptCircleIntersects(
            double startX,
            double startZ,
            double targetX,
            double targetZ,
            PreparationObstacleBox box) {
        if (segmentIntersectsHorizontalBox(startX, startZ, targetX, targetZ, box)) {
            return true;
        }
        double limit = radiusSquaredWithEpsilon();
        if (pointDistanceSquaredToHorizontalBox(startX, startZ, box) <= limit
                || pointDistanceSquaredToHorizontalBox(targetX, targetZ, box) <= limit) {
            return true;
        }
        double minimumX = box.minimum().x();
        double maximumX = box.maximum().x();
        double minimumZ = box.minimum().z();
        double maximumZ = box.maximum().z();
        return pointDistanceSquaredToSegment(
                                minimumX, minimumZ, startX, startZ, targetX, targetZ)
                        <= limit
                || pointDistanceSquaredToSegment(
                                minimumX, maximumZ, startX, startZ, targetX, targetZ)
                        <= limit
                || pointDistanceSquaredToSegment(
                                maximumX, minimumZ, startX, startZ, targetX, targetZ)
                        <= limit
                || pointDistanceSquaredToSegment(
                                maximumX, maximumZ, startX, startZ, targetX, targetZ)
                        <= limit;
    }

    private static boolean segmentIntersectsHorizontalBox(
            double startX,
            double startZ,
            double targetX,
            double targetZ,
            PreparationObstacleBox box) {
        double[] interval = {0.0d, 1.0d};
        return clipAxis(
                        startX,
                        targetX - startX,
                        box.minimum().x(),
                        box.maximum().x(),
                        interval)
                && clipAxis(
                        startZ,
                        targetZ - startZ,
                        box.minimum().z(),
                        box.maximum().z(),
                        interval);
    }

    private static boolean clipAxis(
            double start, double delta, double minimum, double maximum, double[] interval) {
        if (Math.abs(delta) <= COLLISION_EPSILON_METRES) {
            return start >= minimum - COLLISION_EPSILON_METRES
                    && start <= maximum + COLLISION_EPSILON_METRES;
        }
        double first = (minimum - start) / delta;
        double second = (maximum - start) / delta;
        if (first > second) {
            double swapped = first;
            first = second;
            second = swapped;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1] + COLLISION_EPSILON_METRES;
    }

    private static double pointDistanceSquaredToHorizontalBox(
            double x, double z, PreparationObstacleBox box) {
        double closestX = clamp(x, box.minimum().x(), box.maximum().x());
        double closestZ = clamp(z, box.minimum().z(), box.maximum().z());
        double deltaX = x - closestX;
        double deltaZ = z - closestZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static double pointDistanceSquaredToSegment(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double targetX,
            double targetZ) {
        double deltaX = targetX - startX;
        double deltaZ = targetZ - startZ;
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (lengthSquared <= COLLISION_EPSILON_METRES * COLLISION_EPSILON_METRES) {
            double offsetX = pointX - startX;
            double offsetZ = pointZ - startZ;
            return offsetX * offsetX + offsetZ * offsetZ;
        }
        double projection =
                ((pointX - startX) * deltaX + (pointZ - startZ) * deltaZ) / lengthSquared;
        double bounded = clamp(projection, 0.0d, 1.0d);
        double closestX = startX + bounded * deltaX;
        double closestZ = startZ + bounded * deltaZ;
        double offsetX = pointX - closestX;
        double offsetZ = pointZ - closestZ;
        return offsetX * offsetX + offsetZ * offsetZ;
    }

    private static boolean overlapsVertical(
            PreparationObstacleBox box, double startYMetres, double targetYMetres) {
        double minimumBodyY =
                Math.min(startYMetres, targetYMetres)
                        - PLAYER_BODY_RADIUS_METRES
                        - COLLISION_EPSILON_METRES;
        double maximumBodyY =
                Math.max(startYMetres, targetYMetres)
                        + PLAYER_BODY_RADIUS_METRES
                        + COLLISION_EPSILON_METRES;
        return box.maximum().y() >= minimumBodyY && box.minimum().y() <= maximumBodyY;
    }

    private static double radiusSquaredWithEpsilon() {
        double radius = PLAYER_BODY_RADIUS_METRES + COLLISION_EPSILON_METRES;
        return radius * radius;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
