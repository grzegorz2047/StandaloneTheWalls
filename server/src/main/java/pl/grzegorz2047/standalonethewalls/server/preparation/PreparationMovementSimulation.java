package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleMap;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationSupportMap;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationVerticalMotion;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

/** Renderer-independent authoritative fixed-tick movement inside verified map collision. */
public final class PreparationMovementSimulation {
    public static final int TICKS_PER_SECOND = 20;
    public static final int MOVEMENT_SPEED_MILLIMETRES_PER_SECOND = 5_000;
    public static final int SPRINTING_SPEED_MILLIMETRES_PER_SECOND = 8_000;
    public static final int CROUCHING_SPEED_MILLIMETRES_PER_SECOND = 3_000;
    public static final int MAXIMUM_GROUNDED_STEP_MILLIMETRES = 500;
    private static final double WALKING_STEP_MILLIMETRES =
            (double) MOVEMENT_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;
    private static final double SPRINTING_STEP_MILLIMETRES =
            (double) SPRINTING_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;
    private static final double CROUCHING_STEP_MILLIMETRES =
            (double) CROUCHING_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;
    private static final double TICK_SECONDS = 1.0d / TICKS_PER_SECOND;
    private static final int MAXIMUM_JUMP_HEIGHT_MILLIMETRES = 1_000;
    private static final double SUPPORT_TOLERANCE_MILLIMETRES = 1.0d;
    private static final double VERTICAL_COLLISION_TOLERANCE_METRES = 0.000001d;

    private final long roundNumber;
    private final TreeMap<PlayerId, PlayerState> players =
            new TreeMap<>(Comparator.comparing(PlayerId::value));
    private long lastAdvancedTick;

    private PreparationMovementSimulation(long roundNumber, long initialTick) {
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
        if (initialTick < 0L) {
            throw new IllegalArgumentException("initialTick cannot be negative");
        }
        this.roundNumber = roundNumber;
        lastAdvancedTick = initialTick;
    }

    public static PreparationMovementSimulation start(
            long roundNumber,
            long initialTick,
            PreparationMapDefinition map,
            Map<PlayerId, PreparationSpawnAssignment> assignments) {
        PreparationMapDefinition verifiedMap = Objects.requireNonNull(map, "map");
        Map<PlayerId, PreparationSpawnAssignment> initialAssignments =
                Map.copyOf(Objects.requireNonNull(assignments, "assignments"));
        if (initialAssignments.isEmpty()
                || initialAssignments.size() > PreparationWorldSnapshot.MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("assignments size is outside [1, 40]");
        }

        PreparationMovementSimulation simulation =
                new PreparationMovementSimulation(roundNumber, initialTick);
        for (Map.Entry<PlayerId, PreparationSpawnAssignment> entry :
                initialAssignments.entrySet()) {
            PlayerId playerId = Objects.requireNonNull(entry.getKey(), "playerId");
            PreparationSpawnAssignment assignment =
                    Objects.requireNonNull(entry.getValue(), "assignment");
            if (assignment.roundNumber() != roundNumber
                    || !assignment.mapId().equals(verifiedMap.mapId())
                    || !Arrays.equals(assignment.mapSha256(), verifiedMap.mapSha256())) {
                throw new IllegalArgumentException(
                        "preparation assignment does not match the authoritative round and map");
            }
            TeamId team = domainTeam(assignment.team());
            PreparationRegionBounds region = verifiedMap.region(team);
            PlayerState state =
                    PlayerState.atSpawn(
                            assignment,
                            region,
                            verifiedMap.supportMap(),
                            verifiedMap.obstacleMap());
            if (simulation.players.put(playerId, state) != null) {
                throw new IllegalArgumentException("duplicate preparation playerId");
            }
        }
        return simulation;
    }

    public long roundNumber() {
        return roundNumber;
    }

    public int playerCount() {
        return players.size();
    }

    public long lastAdvancedTick() {
        return lastAdvancedTick;
    }

    public boolean remove(PlayerId playerId) {
        return players.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    public PreparationWorldSnapshot advanceTick(
            long authoritativeTick, Map<PlayerId, PreparationInput> latestInputs) {
        if (authoritativeTick <= lastAdvancedTick) {
            throw new IllegalArgumentException("authoritativeTick must advance monotonically");
        }
        Map<PlayerId, PreparationInput> inputs =
                Map.copyOf(Objects.requireNonNull(latestInputs, "latestInputs"));
        for (Map.Entry<PlayerId, PreparationInput> entry : inputs.entrySet()) {
            PlayerState player = players.get(Objects.requireNonNull(entry.getKey(), "playerId"));
            if (player == null) {
                throw new IllegalArgumentException(
                        "input references an unknown preparation player");
            }
            PreparationInput input = Objects.requireNonNull(entry.getValue(), "input");
            if (input.roundNumber() != roundNumber) {
                throw new IllegalArgumentException("input round does not match the simulation");
            }
            player.accept(input);
        }
        for (PlayerState player : players.values()) {
            player.advance();
        }
        lastAdvancedTick = authoritativeTick;
        return snapshot(authoritativeTick);
    }

    public Optional<PreparationWorldSnapshot> currentSnapshot() {
        return players.isEmpty() ? Optional.empty() : Optional.of(snapshot(lastAdvancedTick));
    }

    private PreparationWorldSnapshot snapshot(long authoritativeTick) {
        List<PreparationPlayerSnapshot> snapshots = new ArrayList<>(players.size());
        for (Map.Entry<PlayerId, PlayerState> entry : players.entrySet()) {
            snapshots.add(entry.getValue().snapshot(entry.getKey()));
        }
        return new PreparationWorldSnapshot(roundNumber, authoritativeTick, snapshots);
    }

    private static TeamId domainTeam(LobbyTeam team) {
        return switch (Objects.requireNonNull(team, "team")) {
            case GREEN -> TeamId.GREEN;
            case BLUE -> TeamId.BLUE;
            case RED -> TeamId.RED;
            case YELLOW -> TeamId.YELLOW;
            case UNASSIGNED ->
                    throw new IllegalArgumentException("preparation assignment team is unassigned");
        };
    }

    private static int toMillimetres(double metres) {
        if (!Double.isFinite(metres)) {
            throw new IllegalArgumentException("preparation spawn coordinate must be finite");
        }
        long value = Math.round(metres * 1_000.0d);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("preparation spawn exceeds fixed-point range");
        }
        return (int) value;
    }

    private static int toYawCentidegrees(double yawDegrees) {
        long rounded = Math.round(yawDegrees * 100.0d);
        if (rounded == 18_000L) {
            rounded = -18_000L;
        }
        if (rounded < PreparationInput.MINIMUM_YAW_CENTIDEGREES
                || rounded > PreparationInput.MAXIMUM_YAW_CENTIDEGREES) {
            throw new IllegalArgumentException("preparation spawn yaw exceeds protocol range");
        }
        return (int) rounded;
    }

    private static final class PlayerState {
        private final PreparationRegionBounds region;
        private final PreparationSupportMap supportMap;
        private final PreparationObstacleMap obstacleMap;
        private double xMillimetres;
        private double yMillimetres;
        private double zMillimetres;
        private double verticalVelocityMetresPerSecond;
        private boolean grounded = true;
        private boolean crouching;
        private long consumedJumpSequence;
        private long lastProcessedInputSequence;
        private int yawCentidegrees;
        private int pitchCentidegrees;
        private PreparationInput activeInput;

        private PlayerState(
                PreparationRegionBounds region,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap,
                int xMillimetres,
                int yMillimetres,
                int zMillimetres,
                int yawCentidegrees) {
            this.region = Objects.requireNonNull(region, "region");
            this.supportMap = Objects.requireNonNull(supportMap, "supportMap");
            this.obstacleMap = Objects.requireNonNull(obstacleMap, "obstacleMap");
            if (!region.contains(xMillimetres, yMillimetres, zMillimetres)) {
                throw new IllegalArgumentException(
                        "preparation spawn is outside its authoritative team region");
            }
            double support = supportAtOrBelow(xMillimetres, zMillimetres, yMillimetres);
            if (Math.abs(support - yMillimetres) > SUPPORT_TOLERANCE_MILLIMETRES) {
                throw new IllegalArgumentException(
                        "preparation spawn is not on authoritative collision support");
            }
            if (!obstacleMap.hasPlayerClearance(
                    xMillimetres / 1_000.0d,
                    yMillimetres / 1_000.0d,
                    zMillimetres / 1_000.0d,
                    false)) {
                throw new IllegalArgumentException(
                        "preparation spawn has no authoritative standing clearance");
            }
            if ((long) yMillimetres + MAXIMUM_JUMP_HEIGHT_MILLIMETRES
                    > region.maximumYMillimetres()) {
                throw new IllegalArgumentException(
                        "preparation region has insufficient vertical jump clearance");
            }
            this.xMillimetres = xMillimetres;
            this.yMillimetres = yMillimetres;
            this.zMillimetres = zMillimetres;
            this.yawCentidegrees = yawCentidegrees;
        }

        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap) {
            return new PlayerState(
                    region,
                    supportMap,
                    obstacleMap,
                    toMillimetres(assignment.x()),
                    toMillimetres(assignment.y()),
                    toMillimetres(assignment.z()),
                    toYawCentidegrees(assignment.yawDegrees()));
        }

        private void accept(PreparationInput input) {
            if (input.sequence() <= lastProcessedInputSequence) {
                throw new IllegalArgumentException(
                        "preparation input sequence did not advance monotonically");
            }
            activeInput = input;
            lastProcessedInputSequence = input.sequence();
            yawCentidegrees = input.yawCentidegrees();
            pitchCentidegrees = input.pitchCentidegrees();
        }

        private void advance() {
            PreparationInput input = activeInput;
            if (input != null) {
                applyRequestedPosture(input.crouching());
                advanceHorizontal(input);
            }
            boolean jumpRequested = false;
            if (input != null && input.jumping() && input.sequence() != consumedJumpSequence) {
                jumpRequested = grounded && !crouching;
                consumedJumpSequence = input.sequence();
            }
            double groundYMillimetres = supportAtOrBelow(xMillimetres, zMillimetres, yMillimetres);
            PreparationVerticalMotion.Step vertical =
                    PreparationVerticalMotion.advance(
                            yMillimetres / 1_000.0d,
                            groundYMillimetres / 1_000.0d,
                            verticalVelocityMetresPerSecond,
                            grounded,
                            jumpRequested,
                            TICK_SECONDS);
            double limitedHeightMetres =
                    obstacleMap.limitUpwardMovement(
                            xMillimetres / 1_000.0d,
                            zMillimetres / 1_000.0d,
                            yMillimetres / 1_000.0d,
                            vertical.heightMetres(),
                            crouching);
            if (limitedHeightMetres
                    < vertical.heightMetres() - VERTICAL_COLLISION_TOLERANCE_METRES) {
                vertical = new PreparationVerticalMotion.Step(limitedHeightMetres, 0.0d, false);
            }
            yMillimetres = vertical.heightMetres() * 1_000.0d;
            verticalVelocityMetresPerSecond = vertical.verticalVelocityMetresPerSecond();
            grounded = vertical.grounded();
        }

        private void applyRequestedPosture(boolean requestedCrouching) {
            if (requestedCrouching) {
                crouching = true;
                return;
            }
            if (crouching
                    && obstacleMap.hasPlayerClearance(
                            xMillimetres / 1_000.0d,
                            yMillimetres / 1_000.0d,
                            zMillimetres / 1_000.0d,
                            false)) {
                crouching = false;
            }
        }

        private void advanceHorizontal(PreparationInput input) {
            if (input.forwardAxis() == 0 && input.rightAxis() == 0) {
                return;
            }
            double forward = input.forwardAxisValue();
            double right = input.rightAxisValue();
            double magnitude = Math.hypot(forward, right);
            if (magnitude > 1.0d) {
                forward /= magnitude;
                right /= magnitude;
            }
            double radians = Math.toRadians(input.yawDegrees());
            double forwardX = Math.cos(radians);
            double forwardZ = Math.sin(radians);
            double rightX = -Math.sin(radians);
            double rightZ = Math.cos(radians);
            double step =
                    crouching
                            ? CROUCHING_STEP_MILLIMETRES
                            : input.sprinting()
                                    ? SPRINTING_STEP_MILLIMETRES
                                    : WALKING_STEP_MILLIMETRES;
            double targetX =
                    region.clampX(xMillimetres + step * ((forward * forwardX) + (right * rightX)));
            double targetZ =
                    region.clampZ(zMillimetres + step * ((forward * forwardZ) + (right * rightZ)));
            if (Double.compare(targetX, xMillimetres) == 0
                    && Double.compare(targetZ, zMillimetres) == 0) {
                return;
            }
            double originalX = xMillimetres;
            double originalZ = zMillimetres;
            if (tryMove(targetX, targetZ)) {
                return;
            }
            double deltaX = targetX - originalX;
            double deltaZ = targetZ - originalZ;
            if (Double.compare(deltaX, 0.0d) == 0 || Double.compare(deltaZ, 0.0d) == 0) {
                return;
            }
            if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
                if (!tryMove(targetX, originalZ)) {
                    tryMove(originalX, targetZ);
                }
            } else if (!tryMove(originalX, targetZ)) {
                tryMove(targetX, originalZ);
            }
        }

        private boolean tryMove(double targetX, double targetZ) {
            if (Double.compare(targetX, xMillimetres) == 0
                    && Double.compare(targetZ, zMillimetres) == 0) {
                return false;
            }
            OptionalDouble support =
                    supportMap.highestPlayerCenter(targetX / 1_000.0d, targetZ / 1_000.0d);
            if (support.isEmpty()) {
                return false;
            }
            double supportYMillimetres = support.orElseThrow() * 1_000.0d;
            double targetYMillimetres = yMillimetres;
            boolean targetGrounded = grounded;
            if (grounded) {
                double deltaY = supportYMillimetres - yMillimetres;
                if (deltaY > MAXIMUM_GROUNDED_STEP_MILLIMETRES + SUPPORT_TOLERANCE_MILLIMETRES) {
                    return false;
                }
                if (deltaY >= -MAXIMUM_GROUNDED_STEP_MILLIMETRES - SUPPORT_TOLERANCE_MILLIMETRES) {
                    targetYMillimetres = supportYMillimetres;
                } else {
                    targetGrounded = false;
                }
            } else if (supportYMillimetres > yMillimetres + SUPPORT_TOLERANCE_MILLIMETRES) {
                return false;
            }
            if (!obstacleMap.permitsMovement(
                    xMillimetres / 1_000.0d,
                    yMillimetres / 1_000.0d,
                    zMillimetres / 1_000.0d,
                    targetX / 1_000.0d,
                    targetYMillimetres / 1_000.0d,
                    targetZ / 1_000.0d,
                    crouching)) {
                return false;
            }
            xMillimetres = targetX;
            yMillimetres = targetYMillimetres;
            zMillimetres = targetZ;
            if (targetGrounded) {
                verticalVelocityMetresPerSecond = 0.0d;
            } else if (grounded) {
                verticalVelocityMetresPerSecond = 0.0d;
            }
            grounded = targetGrounded;
            return true;
        }

        private double supportAtOrBelow(
                double playerXMillimetres,
                double playerZMillimetres,
                double maximumPlayerCenterYMillimetres) {
            OptionalDouble support =
                    supportMap.highestPlayerCenterAtOrBelow(
                            playerXMillimetres / 1_000.0d,
                            playerZMillimetres / 1_000.0d,
                            (maximumPlayerCenterYMillimetres + SUPPORT_TOLERANCE_MILLIMETRES)
                                    / 1_000.0d);
            if (support.isEmpty()) {
                throw new IllegalStateException(
                        "authoritative preparation player has no support below it");
            }
            return support.orElseThrow() * 1_000.0d;
        }

        private PreparationPlayerSnapshot snapshot(PlayerId playerId) {
            return new PreparationPlayerSnapshot(
                    playerId,
                    lastProcessedInputSequence,
                    (int) Math.round(xMillimetres),
                    (int) Math.round(yMillimetres),
                    (int) Math.round(zMillimetres),
                    (int) Math.round(verticalVelocityMetresPerSecond * 1_000.0d),
                    grounded,
                    crouching,
                    yawCentidegrees,
                    pitchCentidegrees);
        }
    }
}
