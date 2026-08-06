package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class PreparationMovementProtocolCodecTest {
    private static final int INPUT_ROUND_OFFSET = 1;
    private static final int INPUT_SEQUENCE_OFFSET = INPUT_ROUND_OFFSET + Long.BYTES;
    private static final int INPUT_FORWARD_OFFSET = INPUT_SEQUENCE_OFFSET + Long.BYTES;
    private static final int INPUT_FLAGS_OFFSET = INPUT_FORWARD_OFFSET + 2;
    private static final int INPUT_YAW_OFFSET = INPUT_FLAGS_OFFSET + 1;
    private static final int INPUT_PITCH_OFFSET = INPUT_YAW_OFFSET + Short.BYTES;

    private static final int SNAPSHOT_ROUND_OFFSET = 1;
    private static final int SNAPSHOT_TICK_OFFSET = SNAPSHOT_ROUND_OFFSET + Long.BYTES;
    private static final int SNAPSHOT_COUNT_OFFSET = SNAPSHOT_TICK_OFFSET + Long.BYTES;
    private static final int FIRST_PLAYER_OFFSET =
            PreparationMovementProtocolCodec.SNAPSHOT_HEADER_BYTES;
    private static final int FIRST_SEQUENCE_OFFSET =
            FIRST_PLAYER_OFFSET + PreparationMovementProtocolCodec.PLAYER_ID_BYTES;
    private static final int FIRST_FLAGS_OFFSET = FIRST_SEQUENCE_OFFSET + Long.BYTES;
    private static final int FIRST_X_OFFSET = FIRST_FLAGS_OFFSET + 1;

    @Test
    void encodesTheExactBoundedBigEndianInputVector() throws PreparationProtocolException {
        PreparationInput input = new PreparationInput(3L, 5L, 127, -127, true, 9_000, -2_500);
        byte[] expected =
                ByteBuffer.allocate(PreparationMovementProtocolCodec.INPUT_BYTES)
                        .put((byte) 3)
                        .putLong(3L)
                        .putLong(5L)
                        .put((byte) 127)
                        .put((byte) -127)
                        .put((byte) 1)
                        .putShort((short) 9_000)
                        .putShort((short) -2_500)
                        .array();

        byte[] encoded = PreparationMovementProtocolCodec.encodeInput(input);

        assertThat(encoded).containsExactly(expected);
        assertThat(encoded.length)
                .isLessThanOrEqualTo(MessageType.PREPARATION_INPUT.maximumPayloadBytes());
        assertThat(PreparationMovementProtocolCodec.decodeInput(encoded)).isEqualTo(input);
        assertThat(input.forwardAxisValue()).isEqualTo(1.0d);
        assertThat(input.rightAxisValue()).isEqualTo(-1.0d);
        assertThat(input.sprinting()).isTrue();
        assertThat(input.crouching()).isFalse();
        assertThat(input.yawDegrees()).isEqualTo(90.0d);
        assertThat(input.pitchDegrees()).isEqualTo(-25.0d);
        assertThat(MessageType.PREPARATION_INPUT.channel()).isEqualTo(MessageType.Channel.BOTH);
    }

    @Test
    void encodesWalkingWithAZeroFlagsByte() throws PreparationProtocolException {
        PreparationInput input = new PreparationInput(1L, 2L, 0, 0, false, 0, 0);

        byte[] encoded = PreparationMovementProtocolCodec.encodeInput(input);

        assertThat(encoded[INPUT_FLAGS_OFFSET]).isZero();
        PreparationInput decoded = PreparationMovementProtocolCodec.decodeInput(encoded);
        assertThat(decoded.sprinting()).isFalse();
        assertThat(decoded.crouching()).isFalse();
    }

    @Test
    void encodesTheExactCrouchingInputVector() throws PreparationProtocolException {
        PreparationInput input = new PreparationInput(4L, 9L, -64, 32, false, true, -9_000, 1_500);
        byte[] expected =
                ByteBuffer.allocate(PreparationMovementProtocolCodec.INPUT_BYTES)
                        .put((byte) 3)
                        .putLong(4L)
                        .putLong(9L)
                        .put((byte) -64)
                        .put((byte) 32)
                        .put((byte) 2)
                        .putShort((short) -9_000)
                        .putShort((short) 1_500)
                        .array();

        byte[] encoded = PreparationMovementProtocolCodec.encodeInput(input);

        assertThat(encoded).containsExactly(expected);
        assertThat(PreparationMovementProtocolCodec.decodeInput(encoded)).isEqualTo(input);
        assertThat(input.sprinting()).isFalse();
        assertThat(input.crouching()).isTrue();
    }

    @Test
    void encodesTheExactFixedPointSnapshotVector() throws PreparationProtocolException {
        PreparationPlayerSnapshot player =
                player("a", 7L, 1_250, 2_000, -4_500, true, 4_500, -125);
        PreparationWorldSnapshot snapshot = new PreparationWorldSnapshot(2L, 41L, List.of(player));
        byte[] playerId = player.playerId().value().getBytes(StandardCharsets.US_ASCII);
        byte[] expected =
                ByteBuffer.allocate(
                                PreparationMovementProtocolCodec.SNAPSHOT_HEADER_BYTES
                                        + PreparationMovementProtocolCodec.PLAYER_SNAPSHOT_BYTES)
                        .put((byte) 2)
                        .putLong(2L)
                        .putLong(41L)
                        .put((byte) 1)
                        .put(playerId)
                        .putLong(7L)
                        .put((byte) 1)
                        .putInt(1_250)
                        .putInt(2_000)
                        .putInt(-4_500)
                        .putShort((short) 4_500)
                        .putShort((short) -125)
                        .array();

        byte[] encoded = PreparationMovementProtocolCodec.encodeSnapshot(snapshot);

        assertThat(encoded).containsExactly(expected);
        assertThat(PreparationMovementProtocolCodec.decodeSnapshot(encoded)).isEqualTo(snapshot);
        assertThat(player.crouching()).isTrue();
        assertThat(player.xMetres()).isEqualTo(1.25d);
        assertThat(player.yMetres()).isEqualTo(2.0d);
        assertThat(player.zMetres()).isEqualTo(-4.5d);
        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(player.pitchDegrees()).isEqualTo(-1.25d);
        assertThat(MessageType.PREPARATION_SNAPSHOT.channel()).isEqualTo(MessageType.Channel.BOTH);
    }

    @Test
    void maximumFortyPlayerSnapshotRemainsBelowFourKilobytes() throws PreparationProtocolException {
        List<PreparationPlayerSnapshot> players = new ArrayList<>();
        for (int index = 0; index < PreparationWorldSnapshot.MAXIMUM_PLAYERS; index++) {
            char first = (char) ('a' + index / 26);
            char second = (char) ('a' + index % 26);
            String suffix = "a".repeat(50) + first + second;
            players.add(
                    new PreparationPlayerSnapshot(
                            new PlayerId("sf1_" + suffix),
                            index,
                            index,
                            2_000,
                            -index,
                            index % 2 == 0,
                            0,
                            0));
        }
        PreparationWorldSnapshot snapshot = new PreparationWorldSnapshot(1L, 0L, players);

        byte[] encoded = PreparationMovementProtocolCodec.encodeSnapshot(snapshot);

        assertThat(encoded).hasSize(PreparationMovementProtocolCodec.MAXIMUM_SNAPSHOT_BYTES);
        assertThat(encoded.length).isLessThan(4_096);
        assertThat(encoded.length)
                .isLessThanOrEqualTo(MessageType.PREPARATION_SNAPSHOT.maximumPayloadBytes());
        assertThat(PreparationMovementProtocolCodec.decodeSnapshot(encoded)).isEqualTo(snapshot);
    }

    @Test
    void rejectsMalformedInputEnvelopeAndFields() {
        assertInputCode(null, PreparationProtocolException.Code.INVALID_SIZE);
        assertInputCode(
                new byte[PreparationMovementProtocolCodec.INPUT_BYTES - 1],
                PreparationProtocolException.Code.INVALID_SIZE);
        assertInputCode(
                new byte[PreparationMovementProtocolCodec.INPUT_BYTES + 1],
                PreparationProtocolException.Code.INVALID_SIZE);

        byte[] schemaOne = validInputPayload();
        schemaOne[0] = 1;
        assertInputCode(schemaOne, PreparationProtocolException.Code.UNSUPPORTED_SCHEMA);

        byte[] schemaTwo = validInputPayload();
        schemaTwo[0] = 2;
        assertInputCode(schemaTwo, PreparationProtocolException.Code.UNSUPPORTED_SCHEMA);

        byte[] round = validInputPayload();
        ByteBuffer.wrap(round).putLong(INPUT_ROUND_OFFSET, 0L);
        assertInputCode(round, PreparationProtocolException.Code.INVALID_ROUND_NUMBER);

        byte[] sequence = validInputPayload();
        ByteBuffer.wrap(sequence).putLong(INPUT_SEQUENCE_OFFSET, 0L);
        assertInputCode(sequence, PreparationProtocolException.Code.INVALID_SEQUENCE);

        byte[] axis = validInputPayload();
        axis[INPUT_FORWARD_OFFSET] = (byte) -128;
        assertInputCode(axis, PreparationProtocolException.Code.INVALID_AXIS);

        byte[] unknownFlags = validInputPayload();
        unknownFlags[INPUT_FLAGS_OFFSET] = 4;
        assertInputCode(unknownFlags, PreparationProtocolException.Code.INVALID_STATE);

        byte[] conflictingFlags = validInputPayload();
        conflictingFlags[INPUT_FLAGS_OFFSET] = 3;
        assertInputCode(conflictingFlags, PreparationProtocolException.Code.INVALID_STATE);

        byte[] yaw = validInputPayload();
        ByteBuffer.wrap(yaw).putShort(INPUT_YAW_OFFSET, (short) 18_000);
        assertInputCode(yaw, PreparationProtocolException.Code.INVALID_STATE);

        byte[] pitch = validInputPayload();
        ByteBuffer.wrap(pitch).putShort(INPUT_PITCH_OFFSET, (short) 8_501);
        assertInputCode(pitch, PreparationProtocolException.Code.INVALID_STATE);
    }

    @Test
    void rejectsMalformedSnapshotEnvelopeAndFields() {
        assertSnapshotCode(null, PreparationProtocolException.Code.INVALID_SIZE);
        assertSnapshotCode(
                new byte
                        [PreparationMovementProtocolCodec.SNAPSHOT_HEADER_BYTES
                                + PreparationMovementProtocolCodec.PLAYER_SNAPSHOT_BYTES
                                - 1],
                PreparationProtocolException.Code.INVALID_SIZE);

        byte[] legacySchema = validSnapshotPayload();
        legacySchema[0] = 1;
        assertSnapshotCode(legacySchema, PreparationProtocolException.Code.UNSUPPORTED_SCHEMA);

        byte[] round = validSnapshotPayload();
        ByteBuffer.wrap(round).putLong(SNAPSHOT_ROUND_OFFSET, 0L);
        assertSnapshotCode(round, PreparationProtocolException.Code.INVALID_ROUND_NUMBER);

        byte[] tick = validSnapshotPayload();
        ByteBuffer.wrap(tick).putLong(SNAPSHOT_TICK_OFFSET, -1L);
        assertSnapshotCode(tick, PreparationProtocolException.Code.INVALID_TICK);

        byte[] count = validSnapshotPayload();
        count[SNAPSHOT_COUNT_OFFSET] = 0;
        assertSnapshotCode(count, PreparationProtocolException.Code.INVALID_PLAYER_COUNT);

        byte[] mismatchedCount = validSnapshotPayload();
        mismatchedCount[SNAPSHOT_COUNT_OFFSET] = 2;
        assertSnapshotCode(mismatchedCount, PreparationProtocolException.Code.INVALID_SIZE);

        byte[] playerId = validSnapshotPayload();
        playerId[FIRST_PLAYER_OFFSET] = '?';
        assertSnapshotCode(playerId, PreparationProtocolException.Code.INVALID_PLAYER_ID);

        byte[] sequence = validSnapshotPayload();
        ByteBuffer.wrap(sequence).putLong(FIRST_SEQUENCE_OFFSET, -1L);
        assertSnapshotCode(sequence, PreparationProtocolException.Code.INVALID_SEQUENCE);

        byte[] unknownFlags = validSnapshotPayload();
        unknownFlags[FIRST_FLAGS_OFFSET] = 2;
        assertSnapshotCode(unknownFlags, PreparationProtocolException.Code.INVALID_STATE);

        byte[] coordinate = validSnapshotPayload();
        ByteBuffer.wrap(coordinate)
                .putInt(
                        FIRST_X_OFFSET,
                        PreparationPlayerSnapshot.MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES + 1);
        assertSnapshotCode(coordinate, PreparationProtocolException.Code.INVALID_COORDINATE);
    }

    @Test
    void rejectsDuplicateOrUnsortedSnapshotPlayers() {
        PreparationWorldSnapshot snapshot =
                new PreparationWorldSnapshot(
                        1L,
                        2L,
                        List.of(player("a", 1L, 0, 0, 0, 0, 0), player("b", 1L, 0, 0, 0, 0, 0)));
        byte[] encoded = PreparationMovementProtocolCodec.encodeSnapshot(snapshot);
        int secondPlayerOffset =
                FIRST_PLAYER_OFFSET + PreparationMovementProtocolCodec.PLAYER_SNAPSHOT_BYTES;
        System.arraycopy(
                encoded,
                FIRST_PLAYER_OFFSET,
                encoded,
                secondPlayerOffset,
                PreparationMovementProtocolCodec.PLAYER_ID_BYTES);

        assertSnapshotCode(encoded, PreparationProtocolException.Code.INVALID_PLAYER_ORDER);
        assertThatThrownBy(
                        () ->
                                new PreparationWorldSnapshot(
                                        1L,
                                        2L,
                                        List.of(
                                                player("b", 0L, 0, 0, 0, 0, 0),
                                                player("a", 0L, 0, 0, 0, 0, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueObjectsRejectOutOfRangeConstruction() {
        assertThatThrownBy(() -> new PreparationInput(1L, 1L, 128, 0, false, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationInput(1L, 1L, 0, 0, true, true, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationPlayerSnapshot(playerId("a"), -1L, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationWorldSnapshot(1L, 0L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] validInputPayload() {
        return PreparationMovementProtocolCodec.encodeInput(
                new PreparationInput(1L, 1L, 0, 0, false, 0, 0));
    }

    private static byte[] validSnapshotPayload() {
        return PreparationMovementProtocolCodec.encodeSnapshot(
                new PreparationWorldSnapshot(1L, 0L, List.of(player("a", 0L, 0, 0, 0, 0, 0))));
    }

    private static PreparationPlayerSnapshot player(
            String suffixCharacter, long sequence, int x, int y, int z, int yaw, int pitch) {
        return player(suffixCharacter, sequence, x, y, z, false, yaw, pitch);
    }

    private static PreparationPlayerSnapshot player(
            String suffixCharacter,
            long sequence,
            int x,
            int y,
            int z,
            boolean crouching,
            int yaw,
            int pitch) {
        return new PreparationPlayerSnapshot(
                playerId(suffixCharacter), sequence, x, y, z, crouching, yaw, pitch);
    }

    private static PlayerId playerId(String suffixCharacter) {
        return new PlayerId("sf1_" + suffixCharacter.repeat(52));
    }

    private static void assertInputCode(byte[] payload, PreparationProtocolException.Code code) {
        assertThatThrownBy(() -> PreparationMovementProtocolCodec.decodeInput(payload))
                .isInstanceOfSatisfying(
                        PreparationProtocolException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static void assertSnapshotCode(byte[] payload, PreparationProtocolException.Code code) {
        assertThatThrownBy(() -> PreparationMovementProtocolCodec.decodeSnapshot(payload))
                .isInstanceOfSatisfying(
                        PreparationProtocolException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
