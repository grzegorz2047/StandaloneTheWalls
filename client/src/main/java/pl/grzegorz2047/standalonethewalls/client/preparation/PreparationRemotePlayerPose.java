package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Renderer-independent presentation pose for one remote preparation player. */
public record PreparationRemotePlayerPose(
        PlayerId playerId,
        double xMetres,
        double yMetres,
        double zMetres,
        double yawDegrees,
        double pitchDegrees) {
    public PreparationRemotePlayerPose {
        Objects.requireNonNull(playerId, "playerId");
        requireFinite(xMetres, "xMetres");
        requireFinite(yMetres, "yMetres");
        requireFinite(zMetres, "zMetres");
        requireFinite(yawDegrees, "yawDegrees");
        requireFinite(pitchDegrees, "pitchDegrees");
        if (yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw new IllegalArgumentException("yawDegrees is outside [-180, 180)");
        }
        if (pitchDegrees < -85.0d || pitchDegrees > 85.0d) {
            throw new IllegalArgumentException("pitchDegrees is outside [-85, 85]");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
