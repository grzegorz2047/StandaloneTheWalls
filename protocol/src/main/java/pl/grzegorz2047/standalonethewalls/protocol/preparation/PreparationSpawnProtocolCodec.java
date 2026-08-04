package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

/** Strict bounded big-endian codec for one authoritative preparation spawn assignment. */
public final class PreparationSpawnProtocolCodec {
    public static final int MINIMUM_ASSIGNMENT_BYTES = 86;
    public static final int MAXIMUM_ASSIGNMENT_BYTES =
            MINIMUM_ASSIGNMENT_BYTES + PreparationSpawnAssignment.MAXIMUM_MAP_ID_BYTES - 1;

    private static final int ASSIGNMENT_SCHEMA_VERSION = 1;
    private static final int FIXED_ASSIGNMENT_BYTES = MINIMUM_ASSIGNMENT_BYTES - 1;

    private PreparationSpawnProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeAssignment(PreparationSpawnAssignment assignment) {
        PreparationSpawnAssignment message = Objects.requireNonNull(assignment, "assignment");
        byte[] mapId = message.mapId().getBytes(StandardCharsets.US_ASCII);
        byte[] payload =
                ByteBuffer.allocate(FIXED_ASSIGNMENT_BYTES + mapId.length)
                        .put((byte) ASSIGNMENT_SCHEMA_VERSION)
                        .putLong(message.rosterRevision())
                        .putLong(message.roundNumber())
                        .put((byte) mapId.length)
                        .put(mapId)
                        .put(message.mapSha256())
                        .put((byte) message.team().wireCode())
                        .putShort((short) message.spawnIndex())
                        .putDouble(message.x())
                        .putDouble(message.y())
                        .putDouble(message.z())
                        .putDouble(message.yawDegrees())
                        .array();
        if (payload.length > MessageType.PREPARATION_SPAWN_ASSIGNMENT.maximumPayloadBytes()) {
            throw new IllegalArgumentException(
                    "preparation spawn assignment exceeds its envelope limit");
        }
        return payload;
    }

    public static PreparationSpawnAssignment decodeAssignment(byte[] payload)
            throws PreparationProtocolException {
        if (payload == null
                || payload.length < MINIMUM_ASSIGNMENT_BYTES
                || payload.length > MAXIMUM_ASSIGNMENT_BYTES
                || payload.length
                        > MessageType.PREPARATION_SPAWN_ASSIGNMENT.maximumPayloadBytes()) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SIZE,
                    "preparation spawn assignment has an invalid size");
        }

        ByteBuffer input = ByteBuffer.wrap(payload);
        int schema = Byte.toUnsignedInt(input.get());
        if (schema != ASSIGNMENT_SCHEMA_VERSION) {
            throw failure(
                    PreparationProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "preparation spawn assignment schema is unsupported");
        }
        long rosterRevision = input.getLong();
        if (rosterRevision < 0L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_REVISION,
                    "preparation roster revision is invalid");
        }
        long roundNumber = input.getLong();
        if (roundNumber < 1L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_ROUND_NUMBER,
                    "preparation round number is invalid");
        }

        int mapIdLength = Byte.toUnsignedInt(input.get());
        int expectedLength = FIXED_ASSIGNMENT_BYTES + mapIdLength;
        if (mapIdLength < 1
                || mapIdLength > PreparationSpawnAssignment.MAXIMUM_MAP_ID_BYTES
                || payload.length != expectedLength) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_MAP_ID,
                    "preparation map id length is invalid");
        }
        byte[] mapIdBytes = new byte[mapIdLength];
        input.get(mapIdBytes);
        for (byte mapIdByte : mapIdBytes) {
            int character = Byte.toUnsignedInt(mapIdByte);
            if (character < 0x21 || character > 0x7e) {
                throw failure(
                        PreparationProtocolException.Code.INVALID_MAP_ID,
                        "preparation map id is not canonical visible ASCII");
            }
        }
        String mapId = new String(mapIdBytes, StandardCharsets.US_ASCII);

        byte[] mapSha256 = new byte[PreparationSpawnAssignment.SHA_256_BYTES];
        input.get(mapSha256);
        LobbyTeam team =
                LobbyTeam.fromWireCode(Byte.toUnsignedInt(input.get()))
                        .filter(candidate -> candidate != LobbyTeam.UNASSIGNED)
                        .orElseThrow(
                                () ->
                                        failure(
                                                PreparationProtocolException.Code.INVALID_TEAM,
                                                "preparation spawn team is invalid"));
        int spawnIndex = Short.toUnsignedInt(input.getShort());
        if (spawnIndex > PreparationSpawnAssignment.MAXIMUM_SPAWN_INDEX) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SPAWN_INDEX,
                    "preparation spawn index is invalid");
        }
        double x = readCoordinate(input, "x");
        double y = readCoordinate(input, "y");
        double z = readCoordinate(input, "z");
        double yawDegrees = input.getDouble();
        if (!Double.isFinite(yawDegrees) || yawDegrees < -180.0d || yawDegrees >= 180.0d) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_COORDINATE,
                    "preparation spawn yaw is invalid");
        }

        try {
            return new PreparationSpawnAssignment(
                    rosterRevision,
                    roundNumber,
                    mapId,
                    mapSha256,
                    team,
                    spawnIndex,
                    x,
                    y,
                    z,
                    yawDegrees);
        } catch (IllegalArgumentException exception) {
            throw new PreparationProtocolException(
                    PreparationProtocolException.Code.INVALID_STATE,
                    "preparation spawn assignment state is inconsistent",
                    exception);
        }
    }

    private static double readCoordinate(ByteBuffer input, String field)
            throws PreparationProtocolException {
        double value = input.getDouble();
        if (!Double.isFinite(value)
                || Math.abs(value) > PreparationSpawnAssignment.MAXIMUM_ABSOLUTE_COORDINATE) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_COORDINATE,
                    "preparation " + field + " coordinate is invalid");
        }
        return value;
    }

    private static PreparationProtocolException failure(
            PreparationProtocolException.Code code, String message) {
        return new PreparationProtocolException(code, message);
    }
}
