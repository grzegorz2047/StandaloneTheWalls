package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationRegion;

/** Immutable local preparation player state constrained to one verified team region. */
public final class PreparationPlayerState {
    private final VerifiedPreparationScene scene;
    private final MapVector3 position;
    private final double yawDegrees;

    private PreparationPlayerState(
            VerifiedPreparationScene scene, MapVector3 position, double yawDegrees) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.position = Objects.requireNonNull(position, "position");
        if (!scene.region().contains(position)) {
            throw new IllegalArgumentException(
                    "preparation player position must remain inside the verified region");
        }
        this.yawDegrees = requireNormalizedYaw(yawDegrees);
    }

    public static PreparationPlayerState atAuthoritativeSpawn(VerifiedPreparationScene scene) {
        VerifiedPreparationScene verifiedScene = Objects.requireNonNull(scene, "scene");
        return new PreparationPlayerState(
                verifiedScene,
                verifiedScene.spawn().position(),
                normalizeYaw(verifiedScene.spawn().yawDegrees()));
    }

    public VerifiedPreparationScene scene() {
        return scene;
    }

    public MapVector3 position() {
        return position;
    }

    public double yawDegrees() {
        return yawDegrees;
    }

    public PreparationPlayerState moveHorizontal(double deltaX, double deltaZ) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaZ, "deltaZ");
        PreparationRegion region = scene.region();
        double nextX =
                clamp(addFinite(position.x(), deltaX), region.minimum().x(), region.maximum().x());
        double nextZ =
                clamp(addFinite(position.z(), deltaZ), region.minimum().z(), region.maximum().z());
        if (Double.compare(nextX, position.x()) == 0 && Double.compare(nextZ, position.z()) == 0) {
            return this;
        }
        return new PreparationPlayerState(
                scene, new MapVector3(nextX, position.y(), nextZ), yawDegrees);
    }

    public PreparationPlayerState rotate(double deltaDegrees) {
        requireFinite(deltaDegrees, "deltaDegrees");
        double nextYaw = normalizeYaw(yawDegrees + deltaDegrees);
        if (Double.compare(nextYaw, yawDegrees) == 0) {
            return this;
        }
        return new PreparationPlayerState(scene, position, nextYaw);
    }

    private static double addFinite(double value, double delta) {
        double result = value + delta;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("preparation movement overflowed map space");
        }
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double normalizeYaw(double value) {
        requireFinite(value, "yawDegrees");
        double normalized = value % 360.0d;
        if (normalized >= 180.0d) {
            normalized -= 360.0d;
        } else if (normalized < -180.0d) {
            normalized += 360.0d;
        }
        return normalized == -0.0d ? 0.0d : normalized;
    }

    private static double requireNormalizedYaw(double value) {
        if (!Double.isFinite(value) || value < -180.0d || value >= 180.0d) {
            throw new IllegalArgumentException(
                    "preparation player yaw must be finite and in [-180, 180)");
        }
        return value;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
