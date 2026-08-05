package pl.grzegorz2047.standalonethewalls.server.preparation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationInput;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationPlayerSnapshot;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationWorldSnapshot;

/** Renderer-independent authoritative fixed-tick movement inside verified team regions. */
public final class PreparationMovementSimulation {
    public static final int TICKS_PER_SECOND = 20;
    public static final int MOVEMENT_SPEED_MILLIMETRES_PER_SECOND = 5_000;
    public static final int SPRINTING_SPEED_MILLIMETRES_PER_SECOND = 8_000;
    private static final double WALKING_STEP_MILLIMETRES =
            (double) MOVEMENT_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;
    private static final double SPRINTING_STEP_MILLIMETRES =
            (double) SPRINTING_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;

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
            PlayerState state = PlayerState.atSpawn(assignment, region);
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
        private final int yMillimetres;
        private double xMillimetres;
        private double zMillimetres;
        private long lastProcessedInputSequence;
        private int yawCentidegrees;
        private int pitchCentidegrees;
        private PreparationInput activeInput;

        private PlayerState(
                PreparationRegionBounds region,
                int xMillimetres,
                int yMillimetres,
                int zMillimetres,
                int yawCentidegrees) {
            this.region = Objects.requireNonNull(region, "region");
            if (!region.contains(xMillimetres, yMillimetres, zMillimetres)) {
                throw new IllegalArgumentException(
                        "preparation spawn is outside its authoritative team region");
            }
            this.xMillimetres = xMillimetres;
            this.yMillimetres = yMillimetres;
            this.zMillimetres = zMillimetres;
            this.yawCentidegrees = yawCentidegrees;
        }

        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment, PreparationRegionBounds region) {
            return new PlayerState(
                    region,
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
            if (input == null || (input.forwardAxis() == 0 && input.rightAxis() == 0)) {
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
            double step = input.sprinting() ? SPRINTING_STEP_MILLIMETRES : WALKING_STEP_MILLIMETRES;
            double deltaX = step * ((forward * forwardX) + (right * rightX));
            double deltaZ = step * ((forward * forwardZ) + (right * rightZ));
            xMillimetres = region.clampX(xMillimetres + deltaX);
            zMillimetres = region.clampZ(zMillimetres + deltaZ);
        }

        private PreparationPlayerSnapshot snapshot(PlayerId playerId) {
            return new PreparationPlayerSnapshot(
                    playerId,
                    lastProcessedInputSequence,
                    (int) Math.round(xMillimetres),
                    yMillimetres,
                    (int) Math.round(zMillimetres),
                    yawCentidegrees,
                    pitchCentidegrees);
        }
    }
}
