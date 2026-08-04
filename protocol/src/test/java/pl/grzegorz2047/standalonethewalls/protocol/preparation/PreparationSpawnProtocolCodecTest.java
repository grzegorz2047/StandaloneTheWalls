package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;

class PreparationSpawnProtocolCodecTest {
    private static final String MAP_ID = "arena-one";
    private static final int REVISION_OFFSET = 1;
    private static final int ROUND_NUMBER_OFFSET = REVISION_OFFSET + Long.BYTES;
    private static final int MAP_ID_LENGTH_OFFSET = ROUND_NUMBER_OFFSET + Long.BYTES;
    private static final int MAP_ID_OFFSET = MAP_ID_LENGTH_OFFSET + 1;
    private static final int DIGEST_OFFSET = MAP_ID_OFFSET + MAP_ID.length();
    private static final int TEAM_OFFSET = DIGEST_OFFSET + PreparationSpawnAssignment.SHA_256_BYTES;
    private static final int SPAWN_INDEX_OFFSET = TEAM_OFFSET + 1;
    private static final int X_OFFSET = SPAWN_INDEX_OFFSET + Short.BYTES;
    private static final int Y_OFFSET = X_OFFSET + Double.BYTES;
    private static final int Z_OFFSET = Y_OFFSET + Double.BYTES;
    private static final int YAW_OFFSET = Z_OFFSET + Double.BYTES;

    @Test
    void encodesTheExactBoundedBigEndianVector() throws PreparationProtocolException {
        PreparationSpawnAssignment assignment = validAssignment();
        byte[] mapId = MAP_ID.getBytes(StandardCharsets.US_ASCII);
        byte[] expected =
                ByteBuffer.allocate(
                                PreparationSpawnProtocolCodec.MINIMUM_ASSIGNMENT_BYTES
                                        - 1
                                        + mapId.length)
                        .put((byte) 1)
                        .putLong(7L)
                        .putLong(2L)
                        .put((byte) mapId.length)
                        .put(mapId)
                        .put(digest())
                        .put((byte) LobbyTeam.GREEN.wireCode())
                        .putShort((short) 33)
                        .putDouble(10.5d)
                        .putDouble(2.0d)
                        .putDouble(-4.25d)
                        .putDouble(90.0d)
                        .array();

        byte[] encoded = PreparationSpawnProtocolCodec.encodeAssignment(assignment);

        assertThat(encoded).containsExactly(expected);
        assertThat(encoded.length)
                .isLessThanOrEqualTo(
                        MessageType.PREPARATION_SPAWN_ASSIGNMENT.maximumPayloadBytes());
        assertThat(PreparationSpawnProtocolCodec.decodeAssignment(encoded)).isEqualTo(assignment);
    }

    @Test
    void assignmentDefensivelyCopiesTheMapDigest() {
        byte[] source = digest();
        PreparationSpawnAssignment assignment = assignmentWithDigest(source);
        source[0] = 99;
        byte[] returned = assignment.mapSha256();
        returned[1] = 99;

        assertThat(assignment.mapSha256()).containsExactly(digest());
    }

    @Test
    void acceptsTheMaximumCanonicalMapId() throws PreparationProtocolException {
        String mapId = "m".repeat(PreparationSpawnAssignment.MAXIMUM_MAP_ID_BYTES);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        0L,
                        1L,
                        mapId,
                        digest(),
                        LobbyTeam.YELLOW,
                        PreparationSpawnAssignment.MAXIMUM_SPAWN_INDEX,
                        0.0d,
                        0.0d,
                        0.0d,
                        -180.0d);

        byte[] encoded = PreparationSpawnProtocolCodec.encodeAssignment(assignment);

        assertThat(encoded).hasSize(PreparationSpawnProtocolCodec.MAXIMUM_ASSIGNMENT_BYTES);
        assertThat(PreparationSpawnProtocolCodec.decodeAssignment(encoded)).isEqualTo(assignment);
    }

    @Test
    void rejectsNullTruncatedAndExtendedPayloads() {
        assertCode(null, PreparationProtocolException.Code.INVALID_SIZE);
        assertCode(
                new byte[PreparationSpawnProtocolCodec.MINIMUM_ASSIGNMENT_BYTES - 1],
                PreparationProtocolException.Code.INVALID_SIZE);
        assertCode(
                new byte[PreparationSpawnProtocolCodec.MAXIMUM_ASSIGNMENT_BYTES + 1],
                PreparationProtocolException.Code.INVALID_SIZE);
    }

    @Test
    void rejectsUnsupportedSchemaRevisionAndRoundNumber() {
        byte[] schema = validPayload();
        schema[0] = 2;
        assertCode(schema, PreparationProtocolException.Code.UNSUPPORTED_SCHEMA);

        byte[] revision = validPayload();
        ByteBuffer.wrap(revision).putLong(REVISION_OFFSET, -1L);
        assertCode(revision, PreparationProtocolException.Code.INVALID_REVISION);

        byte[] round = validPayload();
        ByteBuffer.wrap(round).putLong(ROUND_NUMBER_OFFSET, 0L);
        assertCode(round, PreparationProtocolException.Code.INVALID_ROUND_NUMBER);
    }

    @Test
    void rejectsInvalidMapIdLengthAndCharacters() {
        byte[] emptyMapId = validPayload();
        emptyMapId[MAP_ID_LENGTH_OFFSET] = 0;
        assertCode(emptyMapId, PreparationProtocolException.Code.INVALID_MAP_ID);

        byte[] mismatchedLength = validPayload();
        mismatchedLength[MAP_ID_LENGTH_OFFSET] = (byte) (MAP_ID.length() + 1);
        assertCode(mismatchedLength, PreparationProtocolException.Code.INVALID_MAP_ID);

        byte[] nonCanonicalMapId = validPayload();
        nonCanonicalMapId[MAP_ID_OFFSET] = 0x20;
        assertCode(nonCanonicalMapId, PreparationProtocolException.Code.INVALID_MAP_ID);
    }

    @Test
    void rejectsUnassignedUnknownTeamAndOversizedSpawnIndex() {
        byte[] unassigned = validPayload();
        unassigned[TEAM_OFFSET] = (byte) LobbyTeam.UNASSIGNED.wireCode();
        assertCode(unassigned, PreparationProtocolException.Code.INVALID_TEAM);

        byte[] unknown = validPayload();
        unknown[TEAM_OFFSET] = 99;
        assertCode(unknown, PreparationProtocolException.Code.INVALID_TEAM);

        byte[] spawnIndex = validPayload();
        ByteBuffer.wrap(spawnIndex)
                .putShort(
                        SPAWN_INDEX_OFFSET,
                        (short) (PreparationSpawnAssignment.MAXIMUM_SPAWN_INDEX + 1));
        assertCode(spawnIndex, PreparationProtocolException.Code.INVALID_SPAWN_INDEX);
    }

    @Test
    void rejectsNonFiniteOrUnboundedCoordinatesAndYaw() {
        byte[] x = validPayload();
        ByteBuffer.wrap(x).putDouble(X_OFFSET, Double.NaN);
        assertCode(x, PreparationProtocolException.Code.INVALID_COORDINATE);

        byte[] y = validPayload();
        ByteBuffer.wrap(y)
                .putDouble(
                        Y_OFFSET,
                        PreparationSpawnAssignment.MAXIMUM_ABSOLUTE_COORDINATE + 1.0d);
        assertCode(y, PreparationProtocolException.Code.INVALID_COORDINATE);

        byte[] z = validPayload();
        ByteBuffer.wrap(z).putDouble(Z_OFFSET, Double.NEGATIVE_INFINITY);
        assertCode(z, PreparationProtocolException.Code.INVALID_COORDINATE);

        byte[] yaw = validPayload();
        ByteBuffer.wrap(yaw).putDouble(YAW_OFFSET, 180.0d);
        assertCode(yaw, PreparationProtocolException.Code.INVALID_COORDINATE);
    }

    @Test
    void valueObjectRejectsNonCanonicalOrMutableInputsBeforeEncoding() {
        assertThatThrownBy(
                        () ->
                                new PreparationSpawnAssignment(
                                        0L,
                                        1L,
                                        "map id",
                                        digest(),
                                        LobbyTeam.GREEN,
                                        0,
                                        0.0d,
                                        0.0d,
                                        0.0d,
                                        0.0d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PreparationSpawnAssignment(
                                        0L,
                                        1L,
                                        MAP_ID,
                                        Arrays.copyOf(digest(), 31),
                                        LobbyTeam.GREEN,
                                        0,
                                        0.0d,
                                        0.0d,
                                        0.0d,
                                        0.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PreparationSpawnAssignment validAssignment() {
        return assignmentWithDigest(digest());
    }

    private static PreparationSpawnAssignment assignmentWithDigest(byte[] mapSha256) {
        return new PreparationSpawnAssignment(
                7L,
                2L,
                MAP_ID,
                mapSha256,
                LobbyTeam.GREEN,
                33,
                10.5d,
                2.0d,
                -4.25d,
                90.0d);
    }

    private static byte[] digest() {
        byte[] digest = new byte[PreparationSpawnAssignment.SHA_256_BYTES];
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) index;
        }
        return digest;
    }

    private static byte[] validPayload() {
        return PreparationSpawnProtocolCodec.encodeAssignment(validAssignment());
    }

    private static void assertCode(byte[] payload, PreparationProtocolException.Code code) {
        assertThatThrownBy(() -> PreparationSpawnProtocolCodec.decodeAssignment(payload))
                .isInstanceOfSatisfying(
                        PreparationProtocolException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
