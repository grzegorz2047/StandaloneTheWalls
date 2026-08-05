package pl.grzegorz2047.standalonethewalls.protocol.preparation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Strict fixed-size big-endian codec for preparation input and authoritative snapshots. */
public final class PreparationMovementProtocolCodec {
    public static final int INPUT_BYTES = 23;
    public static final int PLAYER_ID_BYTES = 56;
    public static final int PLAYER_SNAPSHOT_BYTES = 80;
    public static final int SNAPSHOT_HEADER_BYTES = 18;
    public static final int MAXIMUM_SNAPSHOT_BYTES =
            SNAPSHOT_HEADER_BYTES
                    + PreparationWorldSnapshot.MAXIMUM_PLAYERS * PLAYER_SNAPSHOT_BYTES;

    private static final int SCHEMA_VERSION = 1;

    private PreparationMovementProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeInput(PreparationInput input) {
        PreparationInput value = requireInput(input);
        return ByteBuffer.allocate(INPUT_BYTES)
                .put((byte) SCHEMA_VERSION)
                .putLong(value.roundNumber())
                .putLong(value.sequence())
                .put((byte) value.forwardAxis())
                .put((byte) value.rightAxis())
                .putShort((short) value.yawCentidegrees())
                .putShort((short) value.pitchCentidegrees())
                .array();
    }

    public static PreparationInput decodeInput(byte[] payload) throws PreparationProtocolException {
        if (payload == null || payload.length != INPUT_BYTES) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SIZE,
                    "preparation input has an invalid size");
        }
        ByteBuffer input = ByteBuffer.wrap(payload);
        requireSchema(input.get());
        long roundNumber = requireRoundNumber(input.getLong());
        long sequence = requirePositiveSequence(input.getLong());
        int forwardAxis = requireAxis(input.get());
        int rightAxis = requireAxis(input.get());
        int yawCentidegrees = requireYaw(input.getShort());
        int pitchCentidegrees = requirePitch(input.getShort());
        return new PreparationInput(
                roundNumber, sequence, forwardAxis, rightAxis, yawCentidegrees, pitchCentidegrees);
    }

    public static byte[] encodeSnapshot(PreparationWorldSnapshot snapshot) {
        PreparationWorldSnapshot value = requireSnapshot(snapshot);
        int size = SNAPSHOT_HEADER_BYTES + value.players().size() * PLAYER_SNAPSHOT_BYTES;
        ByteBuffer output =
                ByteBuffer.allocate(size)
                        .put((byte) SCHEMA_VERSION)
                        .putLong(value.roundNumber())
                        .putLong(value.authoritativeTick())
                        .put((byte) value.players().size());
        for (PreparationPlayerSnapshot player : value.players()) {
            byte[] playerId = player.playerId().value().getBytes(StandardCharsets.US_ASCII);
            if (playerId.length != PLAYER_ID_BYTES) {
                throw new IllegalArgumentException("playerId has an unexpected canonical size");
            }
            output.put(playerId)
                    .putLong(player.lastProcessedInputSequence())
                    .putInt(player.xMillimetres())
                    .putInt(player.yMillimetres())
                    .putInt(player.zMillimetres())
                    .putShort((short) player.yawCentidegrees())
                    .putShort((short) player.pitchCentidegrees());
        }
        return output.array();
    }

    public static PreparationWorldSnapshot decodeSnapshot(byte[] payload)
            throws PreparationProtocolException {
        if (payload == null
                || payload.length < SNAPSHOT_HEADER_BYTES + PLAYER_SNAPSHOT_BYTES
                || payload.length > MAXIMUM_SNAPSHOT_BYTES) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SIZE,
                    "preparation snapshot has an invalid size");
        }
        ByteBuffer input = ByteBuffer.wrap(payload);
        requireSchema(input.get());
        long roundNumber = requireRoundNumber(input.getLong());
        long authoritativeTick = requireTick(input.getLong());
        int playerCount = Byte.toUnsignedInt(input.get());
        if (playerCount < 1 || playerCount > PreparationWorldSnapshot.MAXIMUM_PLAYERS) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_PLAYER_COUNT,
                    "preparation snapshot player count is invalid");
        }
        int expectedSize = SNAPSHOT_HEADER_BYTES + playerCount * PLAYER_SNAPSHOT_BYTES;
        if (payload.length != expectedSize) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SIZE,
                    "preparation snapshot size does not match player count");
        }

        List<PreparationPlayerSnapshot> players = new ArrayList<>(playerCount);
        String previousPlayerId = null;
        for (int index = 0; index < playerCount; index++) {
            byte[] playerIdBytes = new byte[PLAYER_ID_BYTES];
            input.get(playerIdBytes);
            PlayerId playerId = decodePlayerId(playerIdBytes);
            if (previousPlayerId != null && previousPlayerId.compareTo(playerId.value()) >= 0) {
                throw failure(
                        PreparationProtocolException.Code.INVALID_PLAYER_ORDER,
                        "preparation snapshot players are not strictly ordered");
            }
            previousPlayerId = playerId.value();
            long acknowledgedSequence = requireAcknowledgedSequence(input.getLong());
            int xMillimetres = requireCoordinate(input.getInt());
            int yMillimetres = requireCoordinate(input.getInt());
            int zMillimetres = requireCoordinate(input.getInt());
            int yawCentidegrees = requireYaw(input.getShort());
            int pitchCentidegrees = requirePitch(input.getShort());
            players.add(
                    new PreparationPlayerSnapshot(
                            playerId,
                            acknowledgedSequence,
                            xMillimetres,
                            yMillimetres,
                            zMillimetres,
                            yawCentidegrees,
                            pitchCentidegrees));
        }
        return new PreparationWorldSnapshot(roundNumber, authoritativeTick, players);
    }

    private static PreparationInput requireInput(PreparationInput input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        return input;
    }

    private static PreparationWorldSnapshot requireSnapshot(PreparationWorldSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        return snapshot;
    }

    private static void requireSchema(byte raw) throws PreparationProtocolException {
        if (Byte.toUnsignedInt(raw) != SCHEMA_VERSION) {
            throw failure(
                    PreparationProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "preparation movement schema is unsupported");
        }
    }

    private static long requireRoundNumber(long value) throws PreparationProtocolException {
        if (value < 1L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_ROUND_NUMBER,
                    "preparation movement round number is invalid");
        }
        return value;
    }

    private static long requirePositiveSequence(long value) throws PreparationProtocolException {
        if (value < 1L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SEQUENCE,
                    "preparation input sequence is invalid");
        }
        return value;
    }

    private static long requireAcknowledgedSequence(long value)
            throws PreparationProtocolException {
        if (value < 0L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_SEQUENCE,
                    "preparation acknowledged input sequence is invalid");
        }
        return value;
    }

    private static long requireTick(long value) throws PreparationProtocolException {
        if (value < 0L) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_TICK,
                    "preparation authoritative tick is invalid");
        }
        return value;
    }

    private static int requireAxis(byte raw) throws PreparationProtocolException {
        int value = raw;
        if (value < -PreparationInput.MAXIMUM_AXIS) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_AXIS,
                    "preparation input axis is invalid");
        }
        return value;
    }

    private static int requireYaw(short raw) throws PreparationProtocolException {
        int value = raw;
        if (value < PreparationInput.MINIMUM_YAW_CENTIDEGREES
                || value > PreparationInput.MAXIMUM_YAW_CENTIDEGREES) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_STATE, "preparation yaw is invalid");
        }
        return value;
    }

    private static int requirePitch(short raw) throws PreparationProtocolException {
        int value = raw;
        if (value < PreparationInput.MINIMUM_PITCH_CENTIDEGREES
                || value > PreparationInput.MAXIMUM_PITCH_CENTIDEGREES) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_STATE,
                    "preparation pitch is invalid");
        }
        return value;
    }

    private static int requireCoordinate(int value) throws PreparationProtocolException {
        if (value < -PreparationPlayerSnapshot.MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES
                || value > PreparationPlayerSnapshot.MAXIMUM_ABSOLUTE_COORDINATE_MILLIMETRES) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_COORDINATE,
                    "preparation snapshot coordinate is invalid");
        }
        return value;
    }

    private static PlayerId decodePlayerId(byte[] bytes) throws PreparationProtocolException {
        try {
            return new PlayerId(new String(bytes, StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw failure(
                    PreparationProtocolException.Code.INVALID_PLAYER_ID,
                    "preparation snapshot playerId is invalid");
        }
    }

    private static PreparationProtocolException failure(
            PreparationProtocolException.Code code, String message) {
        return new PreparationProtocolException(code, message);
    }
}
