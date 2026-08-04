package pl.grzegorz2047.standalonethewalls.client.preparation;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;

/** Applies the verified preparation player position and bounded protocol view to the camera. */
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
        double pitchRadians = Math.toRadians(player.pitchDegrees());
        double horizontal = Math.cos(pitchRadians);
        target.lookAtDirection(
                new Vector3f(
                        (float)
                                (PreparationFacing.forwardX(player.yawDegrees()) * horizontal),
                        (float) Math.sin(pitchRadians),
                        (float)
                                (PreparationFacing.forwardZ(player.yawDegrees()) * horizontal)),
                Vector3f.UNIT_Y);
    }
}
