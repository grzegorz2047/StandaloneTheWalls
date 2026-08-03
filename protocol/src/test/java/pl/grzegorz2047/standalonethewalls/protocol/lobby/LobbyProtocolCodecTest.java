package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class LobbyProtocolCodecTest {
    private static final String BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";

    @Test
    void encodesJoinedWithExactVersionRevisionAndCanonicalMemberLayout()
            throws LobbyProtocolException {
        LobbyMember self = member("a", "player_one");
        byte[] payload = LobbyProtocolCodec.encodeJoined(new LobbyJoined(7L, self));

        assertEquals(66 + self.handle().value().length(), payload.length);
        assertEquals(1, Byte.toUnsignedInt(payload[0]));
        assertEquals(7L, ByteBuffer.wrap(payload, 1, Long.BYTES).getLong());
        assertArrayEquals(
                self.playerId().value().getBytes(StandardCharsets.US_ASCII),
                Arrays.copyOfRange(payload, 1 + Long.BYTES, 1 + Long.BYTES + 56));
        assertEquals(
                self.handle().value().length(), Byte.toUnsignedInt(payload[1 + Long.BYTES + 56]));
        assertEquals(new LobbyJoined(7L, self), LobbyProtocolCodec.decodeJoined(payload));
    }

    @Test
    void roundTripsACompleteStrictlySortedSchemaTwoRoster() throws LobbyProtocolException {
        LobbySnapshot snapshot =
                new LobbySnapshot(
                        11L,
                        List.of(
                                member("a", "alpha"),
                                member("b", "bravo", LobbyTeam.GREEN, true)));

        byte[] payload = LobbyProtocolCodec.encodeSnapshot(snapshot);

        assertEquals(2, Byte.toUnsignedInt(payload[0]));
        assertEquals(snapshot, LobbyProtocolCodec.decodeSnapshot(payload));
        assertTrue(payload.length <= MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
    }

    @Test
    void decodesLegacySchemaOneMembershipAsUnassignedAndNotReady()
            throws LobbyProtocolException {
        LobbyMember member = member("a", "alpha");
        byte[] playerId = member.playerId().value().getBytes(StandardCharsets.US_ASCII);
        byte[] handle = member.handle().value().getBytes(StandardCharsets.US_ASCII);
        byte[] payload =
                ByteBuffer.allocate(1 + Long.BYTES + 1 + playerId.length + 1 + handle.length)
                        .put((byte) 1)
                        .putLong(5L)
                        .put((byte) 1)
                        .put(playerId)
                        .put((byte) handle.length)
                        .put(handle)
                        .array();

        LobbySnapshot decoded = LobbyProtocolCodec.decodeSnapshot(payload);

        assertEquals(new LobbySnapshot(5L, List.of(member)), decoded);
        assertEquals(LobbyTeam.UNASSIGNED, decoded.members().getFirst().team());
        assertFalse(decoded.members().getFirst().ready());
    }

    @Test
    void supportsTheConfiguredMaximumOfFortyRosterMembers() throws LobbyProtocolException {
        List<LobbyMember> members = new ArrayList<>();
        LobbyTeam[] teams = {LobbyTeam.GREEN, LobbyTeam.BLUE, LobbyTeam.RED, LobbyTeam.YELLOW};
        for (int index = 0; index < LobbySnapshot.MAXIMUM_MEMBERS; index++) {
            char first = BASE32_ALPHABET.charAt(index / BASE32_ALPHABET.length());
            char second = BASE32_ALPHABET.charAt(index % BASE32_ALPHABET.length());
            members.add(
                    new LobbyMember(
                            new PlayerId("sf1_" + first + second + "a".repeat(50)),
                            new CanonicalHandle("player_" + index),
                            teams[index % teams.length],
                            index % 2 == 0));
        }
        members.sort(Comparator.comparing(member -> member.playerId().value()));
        LobbySnapshot snapshot = new LobbySnapshot(42L, members);

        byte[] payload = LobbyProtocolCodec.encodeSnapshot(snapshot);

        assertEquals(snapshot, LobbyProtocolCodec.decodeSnapshot(payload));
        assertTrue(payload.length <= MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
    }

    @Test
    void roundTripsExactTeamReadyAndResultPayloads() throws LobbyProtocolException {
        LobbySelectTeamCommand select = new LobbySelectTeamCommand(9L, LobbyTeam.YELLOW);
        byte[] selectPayload = LobbyProtocolCodec.encodeSelectTeam(select);
        assertEquals(10, selectPayload.length);
        assertEquals(1, Byte.toUnsignedInt(selectPayload[0]));
        assertEquals(9L, ByteBuffer.wrap(selectPayload, 1, Long.BYTES).getLong());
        assertEquals(4, Byte.toUnsignedInt(selectPayload[9]));
        assertEquals(select, LobbyProtocolCodec.decodeSelectTeam(selectPayload));

        LobbySetReadyCommand ready = new LobbySetReadyCommand(10L, true);
        byte[] readyPayload = LobbyProtocolCodec.encodeSetReady(ready);
        assertEquals(10, readyPayload.length);
        assertEquals(1, Byte.toUnsignedInt(readyPayload[9]));
        assertEquals(ready, LobbyProtocolCodec.decodeSetReady(readyPayload));

        LobbyCommandResult result =
                new LobbyCommandResult(10L, 17L, LobbyCommandOutcome.TEAM_IMBALANCE);
        byte[] resultPayload = LobbyProtocolCodec.encodeCommandResult(result);
        assertEquals(18, resultPayload.length);
        assertEquals(15, Byte.toUnsignedInt(resultPayload[17]));
        assertEquals(result, LobbyProtocolCodec.decodeCommandResult(resultPayload));
    }

    @Test
    void rejectsDuplicateAndNonCanonicalMemberOrderAtTheModelBoundary() {
        LobbyMember first = member("a", "alpha");
        LobbyMember second = member("b", "bravo");

        assertThrows(
                IllegalArgumentException.class, () -> new LobbySnapshot(1L, List.of(first, first)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LobbySnapshot(1L, List.of(second, first)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LobbyMember(
                                first.playerId(),
                                first.handle(),
                                LobbyTeam.UNASSIGNED,
                                true));
    }

    @Test
    void rejectsUnsupportedSchemaInvalidRevisionAndTrailingBytes() {
        byte[] joined = LobbyProtocolCodec.encodeJoined(new LobbyJoined(1L, member("a", "alpha")));
        byte[] unsupported = joined.clone();
        unsupported[0] = 2;
        byte[] invalidRevision = joined.clone();
        Arrays.fill(invalidRevision, 1, 1 + Long.BYTES, (byte) 0);
        byte[] trailing = Arrays.copyOf(joined, joined.length + 1);

        assertCode(
                LobbyProtocolException.Code.UNSUPPORTED_SCHEMA,
                () -> LobbyProtocolCodec.decodeJoined(unsupported));
        assertCode(
                LobbyProtocolException.Code.INVALID_REVISION,
                () -> LobbyProtocolCodec.decodeJoined(invalidRevision));
        assertCode(
                LobbyProtocolException.Code.TRAILING_BYTES,
                () -> LobbyProtocolCodec.decodeJoined(trailing));
    }

    @Test
    void rejectsOversizedCountAndDuplicateMemberOnDecode() {
        byte[] oversizedCount =
                ByteBuffer.allocate(10).put((byte) 2).putLong(1L).put((byte) 41).array();
        LobbySnapshot valid =
                new LobbySnapshot(1L, List.of(member("a", "aaa"), member("b", "bbb")));
        byte[] duplicate = LobbyProtocolCodec.encodeSnapshot(valid);
        int firstPlayerIdOffset = 10;
        int secondPlayerIdOffset = firstPlayerIdOffset + 56 + 1 + 3 + 2;
        System.arraycopy(duplicate, firstPlayerIdOffset, duplicate, secondPlayerIdOffset, 56);

        assertCode(
                LobbyProtocolException.Code.INVALID_MEMBER_COUNT,
                () -> LobbyProtocolCodec.decodeSnapshot(oversizedCount));
        assertCode(
                LobbyProtocolException.Code.DUPLICATE_MEMBER,
                () -> LobbyProtocolCodec.decodeSnapshot(duplicate));
    }

    @Test
    void rejectsInvalidRosterTeamBooleanAndReadyState() {
        LobbySnapshot valid = new LobbySnapshot(1L, List.of(member("a", "alpha")));
        byte[] invalidTeam = LobbyProtocolCodec.encodeSnapshot(valid);
        int stateOffset = 10 + 56 + 1 + "alpha".length();
        invalidTeam[stateOffset] = 99;
        byte[] invalidBoolean = LobbyProtocolCodec.encodeSnapshot(valid);
        invalidBoolean[stateOffset + 1] = 2;
        byte[] invalidReadyState = LobbyProtocolCodec.encodeSnapshot(valid);
        invalidReadyState[stateOffset + 1] = 1;

        assertCode(
                LobbyProtocolException.Code.INVALID_TEAM,
                () -> LobbyProtocolCodec.decodeSnapshot(invalidTeam));
        assertCode(
                LobbyProtocolException.Code.INVALID_BOOLEAN,
                () -> LobbyProtocolCodec.decodeSnapshot(invalidBoolean));
        assertCode(
                LobbyProtocolException.Code.INVALID_READY_STATE,
                () -> LobbyProtocolCodec.decodeSnapshot(invalidReadyState));
    }

    @Test
    void rejectsInvalidCommandFieldsAndTrailingBytes() {
        byte[] zeroRequest =
                ByteBuffer.allocate(10).put((byte) 1).putLong(0L).put((byte) 1).array();
        byte[] unknownTeam =
                ByteBuffer.allocate(10).put((byte) 1).putLong(1L).put((byte) 99).array();
        byte[] unassignedTeam =
                ByteBuffer.allocate(10).put((byte) 1).putLong(1L).put((byte) 0).array();
        byte[] invalidBoolean =
                ByteBuffer.allocate(10).put((byte) 1).putLong(1L).put((byte) 2).array();
        byte[] trailing =
                Arrays.copyOf(
                        LobbyProtocolCodec.encodeSelectTeam(
                                new LobbySelectTeamCommand(1L, LobbyTeam.GREEN)),
                        11);
        byte[] invalidOutcome =
                ByteBuffer.allocate(18)
                        .put((byte) 1)
                        .putLong(1L)
                        .putLong(1L)
                        .put((byte) 99)
                        .array();

        assertCode(
                LobbyProtocolException.Code.INVALID_REQUEST_ID,
                () -> LobbyProtocolCodec.decodeSelectTeam(zeroRequest));
        assertCode(
                LobbyProtocolException.Code.INVALID_TEAM,
                () -> LobbyProtocolCodec.decodeSelectTeam(unknownTeam));
        assertCode(
                LobbyProtocolException.Code.INVALID_TEAM,
                () -> LobbyProtocolCodec.decodeSelectTeam(unassignedTeam));
        assertCode(
                LobbyProtocolException.Code.INVALID_BOOLEAN,
                () -> LobbyProtocolCodec.decodeSetReady(invalidBoolean));
        assertCode(
                LobbyProtocolException.Code.TRAILING_BYTES,
                () -> LobbyProtocolCodec.decodeSelectTeam(trailing));
        assertCode(
                LobbyProtocolException.Code.INVALID_OUTCOME,
                () -> LobbyProtocolCodec.decodeCommandResult(invalidOutcome));
    }

    @Test
    void rejectsTruncatedHandleAndInvalidPlayerId() {
        byte[] joined = LobbyProtocolCodec.encodeJoined(new LobbyJoined(1L, member("a", "alpha")));
        byte[] truncated = Arrays.copyOf(joined, joined.length - 1);
        byte[] invalidPlayerId = joined.clone();
        invalidPlayerId[1 + Long.BYTES] = (byte) '!';

        assertCode(
                LobbyProtocolException.Code.INVALID_SIZE,
                () -> LobbyProtocolCodec.decodeJoined(truncated));
        assertCode(
                LobbyProtocolException.Code.INVALID_PLAYER_ID,
                () -> LobbyProtocolCodec.decodeJoined(invalidPlayerId));
    }

    private static LobbyMember member(String prefix, String handle) {
        return new LobbyMember(
                new PlayerId("sf1_" + prefix + "a".repeat(51)), new CanonicalHandle(handle));
    }

    private static LobbyMember member(
            String prefix, String handle, LobbyTeam team, boolean ready) {
        return new LobbyMember(
                new PlayerId("sf1_" + prefix + "a".repeat(51)),
                new CanonicalHandle(handle),
                team,
                ready);
    }

    private static void assertCode(LobbyProtocolException.Code expected, ThrowingDecode operation) {
        LobbyProtocolException exception =
                assertThrows(LobbyProtocolException.class, operation::run);
        assertEquals(expected, exception.code());
    }

    @FunctionalInterface
    private interface ThrowingDecode {
        void run() throws LobbyProtocolException;
    }
}
