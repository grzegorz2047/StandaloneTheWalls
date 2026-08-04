package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;

/** Applies the verified preparation player position and protocol yaw to the camera. */
public final class PreparationCameraPlacement {
    private PreparationCameraPlacement() {
        throw new AssertionError("No instances");
    }

    public static void apply(Camera camera, PreparationPlayerState playerState) {
        Camera target = Objects.requireNonNull(camera, "camera");
        PreparationPlayerState player = Objects.requireNonNull(playerState, "playerState");
        MapVector3 position = player.position();
        target.setLocation(
                new Vector3f((float) position.x(), (float) position.y(), (float) position.z()));
        target.lookAtDirection(
                new Vector3f(
                        (float) PreparationFacing.forwardX(player.yawDegrees()),
                        0.0f,
                        (float) PreparationFacing.forwardZ(player.yawDegrees())),
                Vector3f.UNIT_Y);
    }
}
