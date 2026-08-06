package pl.grzegorz2047.standalonethewalls.client.preparation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

/**
 * Keeps a bounded current/target pair and smooths remote players without extrapolating beyond the
 * newest authoritative snapshot.
 */
public final class PreparationRemoteSnapshotInterpolator {
    public static final double DEFAULT_INTERPOLATION_SECONDS = 0.1d;

    private final long roundNumber;
    private final PlayerId localPlayerId;
    private final double interpolationSeconds;

    private Map<PlayerId, PreparationRemotePlayerPose> start = Map.of();
    private Map<PlayerId, PreparationRemotePlayerPose> target = Map.of();
    private Map<PlayerId, PreparationRemotePlayerPose> presented = Map.of();
    private long latestAuthoritativeTick = -1L;
    private double elapsedSeconds;

    public PreparationRemoteSnapshotInterpolator(long roundNumber, PlayerId localPlayerId) {
        this(roundNumber, localPlayerId, DEFAULT_INTERPOLATION_SECONDS);
    }

    PreparationRemoteSnapshotInterpolator(
            long roundNumber, PlayerId localPlayerId, double interpolationSeconds) {
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        this.localPlayerId = Objects.requireNonNull(localPlayerId, "localPlayerId");
        if (!Double.isFinite(interpolationSeconds) || interpolationSeconds <= 0.0d) {
            throw new IllegalArgumentException("interpolationSeconds must be finite and positive");
        }
        this.roundNumber = roundNumber;
        this.interpolationSeconds = interpolationSeconds;
    }

    public void offer(PreparationWorldSnapshot snapshot) {
        PreparationWorldSnapshot authoritative = Objects.requireNonNull(snapshot, "snapshot");
        if (authoritative.roundNumber() != roundNumber) {
            throw new IllegalArgumentException("snapshot round does not match the interpolator");
        }
        if (authoritative.authoritativeTick() <= latestAuthoritativeTick) {
            throw new IllegalArgumentException("snapshot tick must increase strictly");
        }

        Map<PlayerId, PreparationRemotePlayerPose> nextTarget = remotePoses(authoritative);
        if (latestAuthoritativeTick < 0L) {
            start = nextTarget;
            target = nextTarget;
            presented = nextTarget;
            elapsedSeconds = interpolationSeconds;
        } else {
            Map<PlayerId, PreparationRemotePlayerPose> nextStart = new LinkedHashMap<>();
            for (Map.Entry<PlayerId, PreparationRemotePlayerPose> entry : nextTarget.entrySet()) {
                nextStart.put(
                        entry.getKey(), presented.getOrDefault(entry.getKey(), entry.getValue()));
            }
            start = immutableOrdered(nextStart);
            target = nextTarget;
            presented = start;
            elapsedSeconds = 0.0d;
        }
        latestAuthoritativeTick = authoritative.authoritativeTick();
    }

    public List<PreparationRemotePlayerPose> advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0d) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        if (latestAuthoritativeTick < 0L) {
            return List.of();
        }
        elapsedSeconds = Math.min(interpolationSeconds, elapsedSeconds + deltaSeconds);
        double alpha = elapsedSeconds / interpolationSeconds;
        presented = interpolate(alpha);
        return List.copyOf(presented.values());
    }

    public List<PreparationRemotePlayerPose> current() {
        return List.copyOf(presented.values());
    }

    public long latestAuthoritativeTick() {
        return latestAuthoritativeTick;
    }

    private Map<PlayerId, PreparationRemotePlayerPose> interpolate(double alpha) {
        Map<PlayerId, PreparationRemotePlayerPose> result = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, PreparationRemotePlayerPose> entry : target.entrySet()) {
            PreparationRemotePlayerPose destination = entry.getValue();
            PreparationRemotePlayerPose source = start.getOrDefault(entry.getKey(), destination);
            result.put(entry.getKey(), interpolate(source, destination, alpha));
        }
        return immutableOrdered(result);
    }

    private static PreparationRemotePlayerPose interpolate(
            PreparationRemotePlayerPose source,
            PreparationRemotePlayerPose destination,
            double alpha) {
        return new PreparationRemotePlayerPose(
                destination.playerId(),
                lerp(source.xMetres(), destination.xMetres(), alpha),
                lerp(source.yMetres(), destination.yMetres(), alpha),
                lerp(source.zMetres(), destination.zMetres(), alpha),
                interpolateYaw(source.yawDegrees(), destination.yawDegrees(), alpha),
                lerp(source.pitchDegrees(), destination.pitchDegrees(), alpha),
                lerp(source.crouchAmount(), destination.crouchAmount(), alpha));
    }

    private Map<PlayerId, PreparationRemotePlayerPose> remotePoses(
            PreparationWorldSnapshot snapshot) {
        Map<PlayerId, PreparationRemotePlayerPose> poses = new LinkedHashMap<>();
        for (PreparationPlayerSnapshot player : snapshot.players()) {
            if (!player.playerId().equals(localPlayerId)) {
                poses.put(
                        player.playerId(),
                        new PreparationRemotePlayerPose(
                                player.playerId(),
                                player.xMetres(),
                                player.yMetres(),
                                player.zMetres(),
                                player.yawDegrees(),
                                player.pitchDegrees(),
                                player.crouching() ? 1.0d : 0.0d));
            }
        }
        return immutableOrdered(poses);
    }

    private static Map<PlayerId, PreparationRemotePlayerPose> immutableOrdered(
            Map<PlayerId, PreparationRemotePlayerPose> poses) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(poses));
    }

    private static double interpolateYaw(double source, double destination, double alpha) {
        double delta = normalizeYaw(destination - source);
        return normalizeYaw(source + (delta * alpha));
    }

    private static double normalizeYaw(double value) {
        double normalized = value % 360.0d;
        if (normalized < -180.0d) {
            normalized += 360.0d;
        } else if (normalized >= 180.0d) {
            normalized -= 360.0d;
        }
        return normalized == -0.0d ? 0.0d : normalized;
    }

    private static double lerp(double source, double destination, double alpha) {
        return source + ((destination - source) * alpha);
    }
}
