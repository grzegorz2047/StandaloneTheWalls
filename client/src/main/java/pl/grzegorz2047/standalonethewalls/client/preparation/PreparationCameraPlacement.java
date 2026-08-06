package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;

/** Applies the verified preparation player position and bounded protocol view to the camera. */
public final class PreparationCameraPlacement {
    public static final double CROUCHING_CAMERA_DROP_METRES = 0.65d;

    private PreparationCameraPlacement() {
        throw new AssertionError("No instances");
    }

    public static void apply(Camera camera, PreparationPlayerState playerState) {
        apply(camera, playerState, false);
    }

    public static void apply(Camera camera, PreparationPlayerState playerState, boolean crouching) {
        Camera target = Objects.requireNonNull(camera, "camera");
        PreparationPlayerState player = Objects.requireNonNull(playerState, "playerState");
        MapVector3 position = player.position();
        boolean effectiveCrouching = crouching || player.crouching();
        double cameraY = position.y() - (effectiveCrouching ? CROUCHING_CAMERA_DROP_METRES : 0.0d);
        target.setLocation(
                new Vector3f((float) position.x(), (float) cameraY, (float) position.z()));
        double pitchRadians = Math.toRadians(player.pitchDegrees());
        double horizontal = Math.cos(pitchRadians);
        target.lookAtDirection(
                new Vector3f(
                        (float) (PreparationFacing.forwardX(player.yawDegrees()) * horizontal),
                        (float) Math.sin(pitchRadians),
                        (float) (PreparationFacing.forwardZ(player.yawDegrees()) * horizontal)),
                Vector3f.UNIT_Y);
    }
}
