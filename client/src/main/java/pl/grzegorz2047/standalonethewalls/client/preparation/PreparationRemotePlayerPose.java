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
        double pitchDegrees,
        double crouchAmount) {
    public PreparationRemotePlayerPose(
            PlayerId playerId,
            double xMetres,
            double yMetres,
            double zMetres,
            double yawDegrees,
            double pitchDegrees) {
        this(playerId, xMetres, yMetres, zMetres, yawDegrees, pitchDegrees, 0.0d);
    }

    public PreparationRemotePlayerPose {
        Objects.requireNonNull(playerId, "playerId");
        requireFinite(xMetres, "xMetres");
        requireFinite(yMetres, "yMetres");
        requireFinite(zMetres, "zMetres");
        requireFinite(yawDegrees, "yawDegrees");
        requireFinite(pitchDegrees, "pitchDegrees");
        requireFinite(crouchAmount, "crouchAmount");
        if (yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw new IllegalArgumentException("yawDegrees is outside [-180, 180)");
        }
        if (pitchDegrees < -85.0d || pitchDegrees > 85.0d) {
            throw new IllegalArgumentException("pitchDegrees is outside [-85, 85]");
        }
        if (crouchAmount < 0.0d || crouchAmount > 1.0d) {
            throw new IllegalArgumentException("crouchAmount is outside [0, 1]");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
