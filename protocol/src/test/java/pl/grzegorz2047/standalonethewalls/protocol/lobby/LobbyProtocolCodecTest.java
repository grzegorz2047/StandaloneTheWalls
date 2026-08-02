package pl.grzegorz2047.standalonethewalls.protocol.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void roundTripsACompleteStrictlySortedSnapshot() throws LobbyProtocolException {
        LobbySnapshot snapshot =
                new LobbySnapshot(11L, List.of(member("a", "alpha"), member("b", "bravo")));

        byte[] payload = LobbyProtocolCodec.encodeSnapshot(snapshot);

        assertEquals(snapshot, LobbyProtocolCodec.decodeSnapshot(payload));
        assertTrue(payload.length <= MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
    }

    @Test
    void supportsTheConfiguredMaximumOfFortyMembers() throws LobbyProtocolException {
        List<LobbyMember> members = new ArrayList<>();
        for (int index = 0; index < LobbySnapshot.MAXIMUM_MEMBERS; index++) {
            char first = BASE32_ALPHABET.charAt(index / BASE32_ALPHABET.length());
            char second = BASE32_ALPHABET.charAt(index % BASE32_ALPHABET.length());
            members.add(
                    new LobbyMember(
                            new PlayerId("sf1_" + first + second + "a".repeat(50)),
                            new CanonicalHandle("player_" + index)));
        }
        members.sort(Comparator.comparing(member -> member.playerId().value()));
        LobbySnapshot snapshot = new LobbySnapshot(42L, members);

        byte[] payload = LobbyProtocolCodec.encodeSnapshot(snapshot);

        assertEquals(snapshot, LobbyProtocolCodec.decodeSnapshot(payload));
        assertTrue(payload.length <= MessageType.LOBBY_SNAPSHOT.maximumPayloadBytes());
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
                ByteBuffer.allocate(10).put((byte) 1).putLong(1L).put((byte) 41).array();
        LobbySnapshot valid =
                new LobbySnapshot(1L, List.of(member("a", "aaa"), member("b", "bbb")));
        byte[] duplicate = LobbyProtocolCodec.encodeSnapshot(valid);
        int firstPlayerIdOffset = 10;
        int secondPlayerIdOffset = firstPlayerIdOffset + 56 + 1 + 3;
        System.arraycopy(duplicate, firstPlayerIdOffset, duplicate, secondPlayerIdOffset, 56);

        assertCode(
                LobbyProtocolException.Code.INVALID_MEMBER_COUNT,
                () -> LobbyProtocolCodec.decodeSnapshot(oversizedCount));
        assertCode(
                LobbyProtocolException.Code.DUPLICATE_MEMBER,
                () -> LobbyProtocolCodec.decodeSnapshot(duplicate));
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
