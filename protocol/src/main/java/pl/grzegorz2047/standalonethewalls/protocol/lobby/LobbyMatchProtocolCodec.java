package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import java.nio.ByteBuffer;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;

/** Exact fixed-size big-endian codec for authoritative lobby match snapshots. */
public final class LobbyMatchProtocolCodec {
    public static final int SNAPSHOT_BYTES =
            1
                    + Long.BYTES
                    + Long.BYTES
                    + Long.BYTES
                    + 1
                    + Long.BYTES
                    + 1
                    + Long.BYTES
                    + 1;

    private static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private LobbyMatchProtocolCodec() {
        throw new AssertionError("No instances");
    }

    public static byte[] encodeSnapshot(LobbyMatchPhaseSnapshot snapshot) {
        LobbyMatchPhaseSnapshot message = Objects.requireNonNull(snapshot, "snapshot");
        return ByteBuffer.allocate(SNAPSHOT_BYTES)
                .put((byte) SNAPSHOT_SCHEMA_VERSION)
                .putLong(message.revision())
                .putLong(message.rosterRevision())
                .putLong(message.authoritativeTick())
                .put((byte) message.phase().wireCode())
                .putLong(message.ticksRemaining())
                .put((byte) message.connectedPlayers())
                .putLong(message.roundNumber())
                .put((byte) message.cancellationReason().wireCode())
                .array();
    }

    public static LobbyMatchPhaseSnapshot decodeSnapshot(byte[] payload)
            throws LobbyProtocolException {
        if (payload == null
                || payload.length != SNAPSHOT_BYTES
                || payload.length > MessageType.LOBBY_MATCH_SNAPSHOT.maximumPayloadBytes()) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_SIZE,
                    "lobby match snapshot has an invalid size");
        }

        ByteBuffer input = ByteBuffer.wrap(payload);
        int schema = Byte.toUnsignedInt(input.get());
        if (schema != SNAPSHOT_SCHEMA_VERSION) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.UNSUPPORTED_SCHEMA,
                    "lobby match snapshot schema is unsupported");
        }
        long revision = input.getLong();
        long rosterRevision = input.getLong();
        if (revision < 0L || rosterRevision < 0L) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_REVISION,
                    "lobby match snapshot revision is invalid");
        }
        long authoritativeTick = input.getLong();
        if (authoritativeTick < LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_TICK,
                    "lobby match snapshot tick is invalid");
        }
        LobbyMatchPhase phase =
                LobbyMatchPhase.fromWireCode(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new LobbyProtocolException(
                                                LobbyProtocolException.Code.INVALID_MATCH_PHASE,
                                                "lobby match phase is unknown"));
        long ticksRemaining = input.getLong();
        if (ticksRemaining < 0L) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_TICK,
                    "lobby match countdown is negative");
        }
        int connectedPlayers = Byte.toUnsignedInt(input.get());
        if (connectedPlayers > LobbySnapshot.MAXIMUM_MEMBERS) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_MEMBER_COUNT,
                    "lobby match player count exceeds the supported capacity");
        }
        long roundNumber = input.getLong();
        if (roundNumber < 1L) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_ROUND_NUMBER,
                    "lobby match round number is invalid");
        }
        LobbyCountdownCancellationReason cancellationReason =
                LobbyCountdownCancellationReason.fromWireCode(Byte.toUnsignedInt(input.get()))
                        .orElseThrow(
                                () ->
                                        new LobbyProtocolException(
                                                LobbyProtocolException.Code
                                                        .INVALID_CANCELLATION_REASON,
                                                "lobby countdown cancellation reason is unknown"));

        try {
            return new LobbyMatchPhaseSnapshot(
                    revision,
                    rosterRevision,
                    authoritativeTick,
                    phase,
                    ticksRemaining,
                    connectedPlayers,
                    roundNumber,
                    cancellationReason);
        } catch (IllegalArgumentException exception) {
            throw new LobbyProtocolException(
                    LobbyProtocolException.Code.INVALID_MATCH_STATE,
                    "lobby match snapshot state is inconsistent",
                    exception);
        }
    }
}
