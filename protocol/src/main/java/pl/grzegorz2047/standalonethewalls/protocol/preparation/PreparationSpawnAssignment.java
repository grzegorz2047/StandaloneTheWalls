package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.util.Arrays;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** One client-specific, server-authoritative preparation spawn and pinned map identity. */
public record PreparationSpawnAssignment(
        long rosterRevision,
        long roundNumber,
        String mapId,
        byte[] mapSha256,
        LobbyTeam team,
        int spawnIndex,
        double x,
        double y,
        double z,
        double yawDegrees) {
    public static final int MAXIMUM_MAP_ID_BYTES = 64;
    public static final int SHA_256_BYTES = 32;
    public static final int MAXIMUM_SPAWN_INDEX = 4_095;
    public static final double MAXIMUM_ABSOLUTE_COORDINATE = 1_000_000.0d;

    public PreparationSpawnAssignment {
        if (rosterRevision < 0L) {
            throw new IllegalArgumentException("roster revision cannot be negative");
        }
        if (roundNumber < 1L) {
            throw new IllegalArgumentException("round number must be positive");
        }
        mapId = requireCanonicalMapId(mapId);
        Objects.requireNonNull(mapSha256, "mapSha256");
        if (mapSha256.length != SHA_256_BYTES) {
            throw new IllegalArgumentException("map digest must contain exactly 32 bytes");
        }
        mapSha256 = mapSha256.clone();
        Objects.requireNonNull(team, "team");
        if (team == LobbyTeam.UNASSIGNED) {
            throw new IllegalArgumentException("preparation spawn team cannot be unassigned");
        }
        if (spawnIndex < 0 || spawnIndex > MAXIMUM_SPAWN_INDEX) {
            throw new IllegalArgumentException("spawn index is outside the supported range");
        }
        requireCoordinate(x, "x");
        requireCoordinate(y, "y");
        requireCoordinate(z, "z");
        if (!Double.isFinite(yawDegrees) || yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw new IllegalArgumentException("spawn yaw must be finite and in [-180, 180)");
        }
    }

    @Override
    public byte[] mapSha256() {
        return mapSha256.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreparationSpawnAssignment that)) {
            return false;
        }
        return rosterRevision == that.rosterRevision
                && roundNumber == that.roundNumber
                && spawnIndex == that.spawnIndex
                && Double.compare(x, that.x) == 0
                && Double.compare(y, that.y) == 0
                && Double.compare(z, that.z) == 0
                && Double.compare(yawDegrees, that.yawDegrees) == 0
                && mapId.equals(that.mapId)
                && Arrays.equals(mapSha256, that.mapSha256)
                && team == that.team;
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        rosterRevision, roundNumber, mapId, team, spawnIndex, x, y, z, yawDegrees);
        return 31 * result + Arrays.hashCode(mapSha256);
    }

    private static String requireCanonicalMapId(String value) {
        String mapIdentifier = Objects.requireNonNull(value, "mapId");
        if (mapIdentifier.isEmpty() || mapIdentifier.length() > MAXIMUM_MAP_ID_BYTES) {
            throw new IllegalArgumentException("map id length is outside the supported range");
        }
        for (int index = 0; index < mapIdentifier.length(); index++) {
            char character = mapIdentifier.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("map id must use visible canonical ASCII");
            }
        }
        return mapIdentifier;
    }

    private static void requireCoordinate(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > MAXIMUM_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException(
                    field + " coordinate is outside the supported range");
        }
    }
}
