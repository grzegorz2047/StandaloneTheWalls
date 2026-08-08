package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;

class LobbyMatchProtocolCodecTest {
    private static final int REVISION_OFFSET = 1;
    private static final int ROSTER_REVISION_OFFSET = REVISION_OFFSET + Long.BYTES;
    private static final int AUTHORITATIVE_TICK_OFFSET = ROSTER_REVISION_OFFSET + Long.BYTES;
    private static final int PHASE_OFFSET = AUTHORITATIVE_TICK_OFFSET + Long.BYTES;
    private static final int TICKS_REMAINING_OFFSET = PHASE_OFFSET + 1;
    private static final int CONNECTED_PLAYERS_OFFSET = TICKS_REMAINING_OFFSET + Long.BYTES;
    private static final int ROUND_NUMBER_OFFSET = CONNECTED_PLAYERS_OFFSET + 1;
    private static final int CANCELLATION_REASON_OFFSET = ROUND_NUMBER_OFFSET + Long.BYTES;

    @Test
    void encodesTheExactFixedSizeBigEndianVector() throws LobbyProtocolException {
        LobbyMatchPhaseSnapshot snapshot =
                new LobbyMatchPhaseSnapshot(
                        7L,
                        11L,
                        120L,
                        LobbyMatchPhase.START_COUNTDOWN,
                        59L,
                        4,
                        2L,
                        LobbyCountdownCancellationReason.NONE);

        byte[] encoded = LobbyMatchProtocolCodec.encodeSnapshot(snapshot);
        byte[] expected =
                ByteBuffer.allocate(LobbyMatchProtocolCodec.SNAPSHOT_BYTES)
                        .put((byte) 1)
                        .putLong(7L)
                        .putLong(11L)
                        .putLong(120L)
                        .put((byte) 2)
                        .putLong(59L)
                        .put((byte) 4)
                        .putLong(2L)
                        .put((byte) 0)
                        .array();

        assertThat(LobbyMatchProtocolCodec.SNAPSHOT_BYTES).isEqualTo(44);
        assertThat(encoded).containsExactly(expected);
        assertThat(encoded.length)
                .isLessThanOrEqualTo(MessageType.LOBBY_MATCH_SNAPSHOT.maximumPayloadBytes());
        assertThat(LobbyMatchProtocolCodec.decodeSnapshot(encoded)).isEqualTo(snapshot);
    }

    @Test
    void roundTripsAllPublishedMatchPhases() throws LobbyProtocolException {
        LobbyMatchPhaseSnapshot waiting =
                new LobbyMatchPhaseSnapshot(
                        0L,
                        0L,
                        LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK,
                        LobbyMatchPhase.WAITING_FOR_PLAYERS,
                        0L,
                        0,
                        1L,
                        LobbyCountdownCancellationReason.NONE);
        LobbyMatchPhaseSnapshot cancelled =
                new LobbyMatchPhaseSnapshot(
                        5L,
                        8L,
                        42L,
                        LobbyMatchPhase.WAITING_FOR_PLAYERS,
                        0L,
                        2,
                        1L,
                        LobbyCountdownCancellationReason.LOBBY_NOT_READY);
        LobbyMatchPhaseSnapshot preparation =
                new LobbyMatchPhaseSnapshot(
                        9L,
                        12L,
                        99L,
                        LobbyMatchPhase.PREPARATION,
                        1_200L,
                        6,
                        1L,
                        LobbyCountdownCancellationReason.NONE);
        LobbyMatchPhaseSnapshot opening =
                new LobbyMatchPhaseSnapshot(
                        10L,
                        12L,
                        1_299L,
                        LobbyMatchPhase.WALLS_OPENING,
                        100L,
                        6,
                        1L,
                        LobbyCountdownCancellationReason.NONE);
        LobbyMatchPhaseSnapshot combat =
                new LobbyMatchPhaseSnapshot(
                        11L,
                        12L,
                        1_399L,
                        LobbyMatchPhase.OPEN_COMBAT,
                        8_400L,
                        6,
                        1L,
                        LobbyCountdownCancellationReason.NONE);

        assertThat(
                        LobbyMatchProtocolCodec.decodeSnapshot(
                                LobbyMatchProtocolCodec.encodeSnapshot(waiting)))
                .isEqualTo(waiting);
        assertThat(
                        LobbyMatchProtocolCodec.decodeSnapshot(
                                LobbyMatchProtocolCodec.encodeSnapshot(cancelled)))
                .isEqualTo(cancelled);
        assertThat(
                        LobbyMatchProtocolCodec.decodeSnapshot(
                                LobbyMatchProtocolCodec.encodeSnapshot(preparation)))
                .isEqualTo(preparation);
        assertThat(
                        LobbyMatchProtocolCodec.decodeSnapshot(
                                LobbyMatchProtocolCodec.encodeSnapshot(opening)))
                .isEqualTo(opening);
        assertThat(
                        LobbyMatchProtocolCodec.decodeSnapshot(
                                LobbyMatchProtocolCodec.encodeSnapshot(combat)))
                .isEqualTo(combat);
    }

    @Test
    void rejectsNullTruncatedAndExtendedPayloads() {
        assertCode(null, LobbyProtocolException.Code.INVALID_SIZE);
        assertCode(
                new byte[LobbyMatchProtocolCodec.SNAPSHOT_BYTES - 1],
                LobbyProtocolException.Code.INVALID_SIZE);
        assertCode(
                new byte[LobbyMatchProtocolCodec.SNAPSHOT_BYTES + 1],
                LobbyProtocolException.Code.INVALID_SIZE);
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidRevisions() {
        byte[] schema = validPayload();
        schema[0] = 2;
        assertCode(schema, LobbyProtocolException.Code.UNSUPPORTED_SCHEMA);

        byte[] revision = validPayload();
        ByteBuffer.wrap(revision).putLong(REVISION_OFFSET, -1L);
        assertCode(revision, LobbyProtocolException.Code.INVALID_REVISION);

        byte[] rosterRevision = validPayload();
        ByteBuffer.wrap(rosterRevision).putLong(ROSTER_REVISION_OFFSET, -1L);
        assertCode(rosterRevision, LobbyProtocolException.Code.INVALID_REVISION);
    }

    @Test
    void rejectsInvalidTickPhasePlayerCountRoundAndCancellationCode() {
        byte[] authoritativeTick = validPayload();
        ByteBuffer.wrap(authoritativeTick).putLong(AUTHORITATIVE_TICK_OFFSET, -2L);
        assertCode(authoritativeTick, LobbyProtocolException.Code.INVALID_TICK);

        byte[] phase = validPayload();
        phase[PHASE_OFFSET] = 99;
        assertCode(phase, LobbyProtocolException.Code.INVALID_MATCH_PHASE);

        byte[] ticksRemaining = validPayload();
        ByteBuffer.wrap(ticksRemaining).putLong(TICKS_REMAINING_OFFSET, -1L);
        assertCode(ticksRemaining, LobbyProtocolException.Code.INVALID_TICK);

        byte[] connectedPlayers = validPayload();
        connectedPlayers[CONNECTED_PLAYERS_OFFSET] = 41;
        assertCode(connectedPlayers, LobbyProtocolException.Code.INVALID_MEMBER_COUNT);

        byte[] roundNumber = validPayload();
        ByteBuffer.wrap(roundNumber).putLong(ROUND_NUMBER_OFFSET, 0L);
        assertCode(roundNumber, LobbyProtocolException.Code.INVALID_ROUND_NUMBER);

        byte[] cancellationReason = validPayload();
        cancellationReason[CANCELLATION_REASON_OFFSET] = 99;
        assertCode(cancellationReason, LobbyProtocolException.Code.INVALID_CANCELLATION_REASON);
    }

    @Test
    void rejectsInternallyInconsistentPhaseState() {
        byte[] waitingWithTicks = validPayload();
        waitingWithTicks[PHASE_OFFSET] = (byte) LobbyMatchPhase.WAITING_FOR_PLAYERS.wireCode();
        assertCode(waitingWithTicks, LobbyProtocolException.Code.INVALID_MATCH_STATE);

        byte[] countdownWithoutTicks = validPayload();
        ByteBuffer.wrap(countdownWithoutTicks).putLong(TICKS_REMAINING_OFFSET, 0L);
        assertCode(countdownWithoutTicks, LobbyProtocolException.Code.INVALID_MATCH_STATE);

        byte[] countdownWithCancellation = validPayload();
        countdownWithCancellation[CANCELLATION_REASON_OFFSET] =
                (byte) LobbyCountdownCancellationReason.INSUFFICIENT_PLAYERS.wireCode();
        assertCode(countdownWithCancellation, LobbyProtocolException.Code.INVALID_MATCH_STATE);
    }

    @Test
    void valueObjectRejectsImpossibleStatesBeforeEncoding() {
        assertThatThrownBy(
                        () ->
                                new LobbyMatchPhaseSnapshot(
                                        0L,
                                        0L,
                                        0L,
                                        LobbyMatchPhase.WAITING_FOR_PLAYERS,
                                        1L,
                                        0,
                                        1L,
                                        LobbyCountdownCancellationReason.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new LobbyMatchPhaseSnapshot(
                                        0L,
                                        0L,
                                        0L,
                                        LobbyMatchPhase.START_COUNTDOWN,
                                        1L,
                                        0,
                                        1L,
                                        LobbyCountdownCancellationReason.LOBBY_NOT_READY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] validPayload() {
        return LobbyMatchProtocolCodec.encodeSnapshot(
                new LobbyMatchPhaseSnapshot(
                        1L,
                        2L,
                        3L,
                        LobbyMatchPhase.START_COUNTDOWN,
                        4L,
                        5,
                        1L,
                        LobbyCountdownCancellationReason.NONE));
    }

    private static void assertCode(byte[] payload, LobbyProtocolException.Code code) {
        assertThatThrownBy(() -> LobbyMatchProtocolCodec.decodeSnapshot(payload))
                .isInstanceOfSatisfying(
                        LobbyProtocolException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
